package com.example.kwai_data.dto.order;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class KsOrderQueryData {
    private String cursor;
    private List<KsOrderDTO> orders;
}


