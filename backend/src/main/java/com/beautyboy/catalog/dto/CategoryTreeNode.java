package com.beautyboy.catalog.dto;

import java.util.List;

public record CategoryTreeNode(String code, String name, int depth, List<CategoryTreeNode> children) {
}
