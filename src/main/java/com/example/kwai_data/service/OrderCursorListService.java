package com.example.kwai_data.service;



import com.example.kwai_data.config.KwaiProperties;
import com.kuaishou.merchant.open.api.client.AccessTokenKsMerchantClient;
import com.kuaishou.merchant.open.api.request.order.OpenOrderCursorListRequest;
import com.kuaishou.merchant.open.api.response.order.OpenOrderCursorListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCursorListService {

    private final AccessTokenKsMerchantClient client;
    private final KwaiProperties props;

    /**
     * 单次拉取（对应 demo）
     */
    public OpenOrderCursorListResponse fetchOnce(
            Integer orderViewStatus,
            Integer pageSize,
            Integer sort,
            Integer queryType,
            Long beginTime,
            Long endTime,
            Integer cpsType,
            String cursor
    ) throws Exception {
        OpenOrderCursorListRequest request = new OpenOrderCursorListRequest();
        request.setAccessToken(props.getAccessToken02());
        request.setApiMethodVersion(1L);

        if (orderViewStatus != null) request.setOrderViewStatus(orderViewStatus);
        if (pageSize != null) request.setPageSize(pageSize);
        if (sort != null) request.setSort(sort);
        if (queryType != null) request.setQueryType(queryType);
        if (beginTime != null) request.setBeginTime(beginTime);
        if (endTime != null) request.setEndTime(endTime);
        if (cpsType != null) request.setCpsType(cpsType);
        if (cursor != null && !cursor.isBlank()) request.setCursor(cursor);

        return client.execute(request);
    }

    /**
     * 拉取全部（自动翻页）
     *
     * 说明：
     * - pageSize 建议你按接口上限设置
     * - cursor 初始可传 null（从第一页开始）
     */
    public List<OpenOrderCursorListResponse> fetchAllPages(
            Integer orderViewStatus,
            Integer pageSize,
            Integer sort,
            Integer queryType,
            Long beginTime,
            Long endTime,
            Integer cpsType,
            String initialCursor,
            int maxPages
    ) throws Exception {

        List<OpenOrderCursorListResponse> pages = new ArrayList<>();
        String cursor = initialCursor;

        for (int i = 0; i < maxPages; i++) {
            OpenOrderCursorListResponse resp = fetchOnce(
                    orderViewStatus, pageSize, sort, queryType, beginTime, endTime, cpsType, cursor
            );
            pages.add(resp);

            // ====== 下面这一段：你需要根据真实 response 结构确认 nextCursor 字段路径 ======
            String nextCursor = extractNextCursor(resp);

            // 如果没有下一页，退出
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) {
                break;
            }
            cursor = nextCursor;
        }

        return pages;
    }

    /**
     * 从响应中提取下一页 cursor（常见命名：data.cursor / data.nextCursor 等）
     * 你拿到真实 JSON 后，把这里改成准确的 getter 路径即可。
     */
    private String extractNextCursor(OpenOrderCursorListResponse resp) {
        if (resp == null) return null;

        try {
            // 常见结构示例（需你按实际 SDK getter 调整）：
            // return resp.getData().getCursor();
            // 或 return resp.getData().getNextCursor();

            // 这里先返回 null，避免你误用导致死循环
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }
}

