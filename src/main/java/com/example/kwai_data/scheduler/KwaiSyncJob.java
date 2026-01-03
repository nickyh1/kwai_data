package com.example.kwai_data.scheduler;



import com.example.kwai_data.facade.KwaiFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KwaiSyncJob {

    private final KwaiFacade kwaiFacade;

    // 每 3 小时执行一次（上一次结束后再延迟 3 小时更稳）
    //fixedRate：固定执行  fixedDelay：上一轮跑完执行下一次
    @Scheduled(fixedRate = 3 * 60 * 60 * 1000L, initialDelay = 10_000L)
    public void syncAll() throws Exception {
        kwaiFacade.syncAll();
    }
}

