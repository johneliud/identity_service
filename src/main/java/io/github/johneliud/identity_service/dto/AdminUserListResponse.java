package io.github.johneliud.identity_service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListResponse {
    private List<AdminUserSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
