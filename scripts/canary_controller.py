#!/usr/bin/env python3
"""
Canary Deployment Controller
根据 Prometheus 监控指标自动调整灰度发布权重
"""

import os
import time
import requests
import json
import subprocess
from typing import Dict, Optional
from dataclasses import dataclass
from datetime import datetime, timedelta
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

@dataclass
class CanaryConfig:
    """灰度发布配置"""
    namespace: str = "prod"
    release_name: str = "jvm-demo"
    canary_enabled: bool = False
    current_weight: int = 10
    min_weight: int = 5
    max_weight: int = 100
    weight_step: int = 10
    poll_interval: int = 30
    # 阈值配置
    error_rate_threshold: float = 5.0  # 5%
    latency_threshold_ms: float = 500.0  # 500ms
    memory_usage_threshold: float = 80.0  # 80%
    auto_rollback: bool = True
    auto_rollback_errors: int = 3


class PrometheusClient:
    """Prometheus 客户端"""
    
    def __init__(self, url: str):
        self.url = url.rstrip('/')
    
    def query(self, expr: str, time: Optional[float] = None) -> Optional[Dict]:
        params = {'query': expr}
        if time:
            params['time'] = str(time)
        
        try:
            response = requests.get(f"{self.url}/api/v1/query", params=params, timeout=10)
            response.raise_for_status()
            data = response.json()
            if data.get('status') == 'success' and data.get('data', {}).get('result'):
                return data['data']['result']
            return None
        except Exception as e:
            logger.error(f"Prometheus query failed: {e}")
            return None
    
    def get_canary_error_rate(self) -> Optional[float]:
        """获取灰度版本错误率"""
        expr = '''100 * sum(rate(http_server_requests_seconds_count{status=~"5..", job="jvm-demo", version="canary"}[1m]))
/ sum(rate(http_server_requests_seconds_count{job="jvm-demo", version="canary"}[1m]))'''
        result = self.query(expr)
        if result:
            try:
                return float(result[0]['value'][1])
            except:
                pass
        return None
    
    def get_canary_latency(self) -> Optional[float]:
        """获取灰度版本 P99 延迟"""
        expr = 'histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{job="jvm-demo", version="canary"}[1m]))'
        result = self.query(expr)
        if result:
            try:
                return float(result[0]['value'][1]) * 1000  # 转换为 ms
            except:
                pass
        return None
    
    def get_canary_memory_usage(self) -> Optional[float]:
        """获取灰度版本内存使用率"""
        expr = '''100 * avg(jvm_memory_used_bytes{area="heap", job="jvm-demo", version="canary"})
/ avg(jvm_memory_max_bytes{area="heap", job="jvm-demo", version="canary"})'''
        result = self.query(expr)
        if result:
            try:
                return float(result[0]['value'][1])
            except:
                pass
        return None


class HelmController:
    """Helm 控制器"""
    
    def __init__(self, config: CanaryConfig):
        self.config = config
    
    def update_canary_weight(self, weight: int) -> bool:
        """更新灰度权重"""
        try:
            cmd = [
                'helm', 'upgrade', self.config.release_name, 'charts/jvm-demo',
                '--namespace', self.config.namespace,
                '--set', f'canary.weight={weight}',
                '--wait', '--timeout', '5m'
            ]
            logger.info(f"Updating canary weight to {weight}%")
            subprocess.run(cmd, check=True, capture_output=True, text=True)
            self.config.current_weight = weight
            logger.info(f"Successfully updated canary weight to {weight}%")
            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Failed to update canary weight: {e.stderr}")
            return False
    
    def disable_canary(self) -> bool:
        """禁用灰度发布 (回滚) - 同时删除灰度控制器"""
        try:
            cmd = [
                'helm', 'upgrade', self.config.release_name, 'charts/jvm-demo',
                '--namespace', self.config.namespace,
                '--set', 'canary.enabled=false',
                '--set', 'canaryController.enabled=false',
                '--wait', '--timeout', '5m'
            ]
            logger.warning("Disabling canary and controller (rollback)")
            subprocess.run(cmd, check=True, capture_output=True, text=True)
            self.config.canary_enabled = False
            logger.info("Successfully disabled canary and controller")
            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Failed to disable canary: {e.stderr}")
            return False


class CanaryController:
    """灰度发布自动控制器"""
    
    def __init__(self, prometheus_url: str, config: Optional[CanaryConfig] = None):
        self.config = config or CanaryConfig()
        self.prometheus = PrometheusClient(prometheus_url)
        self.helm = HelmController(self.config)
        self.consecutive_errors = 0
        self.last_rollback_time = None
        self.cooldown_period = timedelta(minutes=15)
    
    def evaluate_metrics(self) -> Dict[str, float]:
        """评估监控指标"""
        metrics = {}
        
        error_rate = self.prometheus.get_canary_error_rate()
        if error_rate is not None:
            metrics['error_rate'] = error_rate
        
        latency = self.prometheus.get_canary_latency()
        if latency is not None:
            metrics['latency_ms'] = latency
        
        memory_usage = self.prometheus.get_canary_memory_usage()
        if memory_usage is not None:
            metrics['memory_usage'] = memory_usage
        
        return metrics
    
    def check_health(self, metrics: Dict[str, float]) -> tuple[bool, str]:
        """检查灰度版本健康状态"""
        health_issues = []
        
        # 检查错误率
        if 'error_rate' in metrics:
            if metrics['error_rate'] > self.config.error_rate_threshold:
                health_issues.append(
                    f"Error rate too high: {metrics['error_rate']:.2f}% (threshold: {self.config.error_rate_threshold}%)"
                )
        
        # 检查延迟
        if 'latency_ms' in metrics:
            if metrics['latency_ms'] > self.config.latency_threshold_ms:
                health_issues.append(
                    f"Latency too high: {metrics['latency_ms']:.2f}ms (threshold: {self.config.latency_threshold_ms}ms)"
                )
        
        # 检查内存使用
        if 'memory_usage' in metrics:
            if metrics['memory_usage'] > self.config.memory_usage_threshold:
                health_issues.append(
                    f"Memory usage too high: {metrics['memory_usage']:.2f}% (threshold: {self.config.memory_usage_threshold}%)"
                )
        
        if health_issues:
            return False, "; ".join(health_issues)
        return True, "All metrics healthy"
    
    def auto_adjust(self):
        """自动调整灰度权重"""
        if not self.config.canary_enabled:
            logger.info("Canary not enabled, skipping auto adjustment")
            return
        
        # 检查冷却期
        if self.last_rollback_time and datetime.now() - self.last_rollback_time < self.cooldown_period:
            logger.info("In cooldown period after rollback, skipping adjustment")
            return
        
        metrics = self.evaluate_metrics()
        logger.info(f"Current metrics: {metrics}")
        
        is_healthy, health_message = self.check_health(metrics)
        
        if not is_healthy:
            self.consecutive_errors += 1
            logger.warning(f"Canary unhealthy (consecutive errors: {self.consecutive_errors}): {health_message}")
            
            if self.config.auto_rollback and self.consecutive_errors >= self.config.auto_rollback_errors:
                logger.critical("Triggering auto rollback due to health issues!")
                if self.helm.disable_canary():
                    self.last_rollback_time = datetime.now()
                    self.consecutive_errors = 0
            else:
                # 降低权重
                new_weight = max(self.config.min_weight, self.config.current_weight - self.config.weight_step)
                if new_weight < self.config.current_weight:
                    self.helm.update_canary_weight(new_weight)
        else:
            self.consecutive_errors = 0
            logger.info(health_message)
            
            # 提高权重 (渐进式)
            new_weight = min(self.config.max_weight, self.config.current_weight + self.config.weight_step)
            if new_weight > self.config.current_weight:
                logger.info(f"Gradually increasing canary weight to {new_weight}%")
                self.helm.update_canary_weight(new_weight)
    
    def run_loop(self):
        """运行主循环"""
        logger.info("Starting canary controller loop")
        try:
            while True:
                self.auto_adjust()
                time.sleep(self.config.poll_interval)
        except KeyboardInterrupt:
            logger.info("Canary controller stopped by user")


def main():
    prometheus_url = os.getenv('PROMETHEUS_URL', 'http://prometheus:9090')
    namespace = os.getenv('K8S_NAMESPACE', 'prod')
    
    config = CanaryConfig(
        namespace=namespace,
        canary_enabled=True,
        auto_rollback=True
    )
    
    controller = CanaryController(prometheus_url, config)
    controller.run_loop()


if __name__ == '__main__':
    main()
