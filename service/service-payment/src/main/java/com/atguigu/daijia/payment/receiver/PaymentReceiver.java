package com.atguigu.daijia.payment.receiver;

import com.atguigu.daijia.common.constant.MqConst;
import com.atguigu.daijia.payment.service.WxPayService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentReceiver {

    @Resource
    private WxPayService wxPayService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.ROUTING_PAY_SUCCESS, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_ORDER),
            key = {MqConst.ROUTING_PAY_SUCCESS}
    ))
    public void receivePaySuccess(String orderNo, Message message, Channel channel) {
        System.out.println("支付成功，订单号：" + orderNo);
        wxPayService.handleOrder(orderNo);
    }
}
