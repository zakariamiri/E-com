package org.sid.Product_service.dto;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long parentId
) {
}
