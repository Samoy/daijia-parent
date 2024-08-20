package com.atguigu.daijia.dispatch.xxl.job;

import com.atguigu.daijia.dispatch.mapper.XxlJobLogMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.model.entity.dispatch.XxlJobLog;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * JobHandler
 *
 * @author Samoy
 * @date 2024/8/20
 */
@Component
public class JobHandler {

    @Resource
    private XxlJobLogMapper xxlJobLogMapper;
    @Resource
    private NewOrderService newOrderService;

    @XxlJob("newOrderTaskHandler")
    public void newOrderTaskHandler() {
        XxlJobLog xxlJobLog = new XxlJobLog();
        xxlJobLog.setId(XxlJobHelper.getJobId());
        long startTime = System.currentTimeMillis();
        try {
            // 执行任务：搜索附近代驾司机
            newOrderService.executeTask(XxlJobHelper.getJobId());
            xxlJobLog.setStatus(1);
        } catch (Exception e) {
            xxlJobLog.setStatus(0);
            xxlJobLog.setError(e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            xxlJobLog.setTimes(endTime - startTime);
            xxlJobLogMapper.insert(xxlJobLog);
        }
    }

}
