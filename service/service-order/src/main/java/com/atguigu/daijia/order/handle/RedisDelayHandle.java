package com.atguigu.daijia.order.handle;

import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.order.service.OrderInfoService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class RedisDelayHandle {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private OrderInfoService orderInfoService;

    @PostConstruct
    public void listener() {
        new Thread(() -> {
            while (true) {
                RBoundedBlockingQueue<String> queue = redissonClient.getBoundedBlockingQueue(SystemConstant.QUEUE_CANCEL_ORDER);
                try {
                    String orderId = queue.take();
                    if (StringUtils.hasText(orderId)) {
                        orderInfoService.cancelOrder(Long.valueOf(orderId));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}
