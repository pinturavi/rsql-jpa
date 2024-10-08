package com.pinturavi.rsql;

import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.LogicalOperator;
import org.springframework.data.jpa.domain.Specification;
import java.util.Objects;

import java.util.List;
import java.util.stream.Collectors;


public class GenericRsqlSpecBuilder<T> {

    public Specification<T> createSpecification(final Node node) {

        if (node instanceof LogicalNode) {
            return createSpecification((LogicalNode) node);

        } else if (node instanceof ComparisonNode) {
            return createSpecification((ComparisonNode) node);
        }
        return null;
    }

    private Specification<T> createSpecification(final LogicalNode logicalNode) {

        final List<Specification<T>> specs = logicalNode.getChildren().stream()
                .map(this::createSpecification)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Specification<T> result = specs.get(0);

        if (logicalNode.getOperator() == LogicalOperator.AND) {
            for (int i = 1; i < specs.size(); i++) {
                result = Specification.where(result).and(specs.get(i));
            }
        } else if (logicalNode.getOperator() == LogicalOperator.OR) {
            for (int i = 1; i < specs.size(); i++) {
                result = Specification.where(result).or(specs.get(i));
            }
        }
        return result;
    }

    private Specification<T> createSpecification(final ComparisonNode comparisonNode) {

        return Specification.where(
                new GenericRsqlSpecification<>(
                        comparisonNode.getSelector(),
                        comparisonNode.getOperator(),
                        comparisonNode.getArguments()
                )
        );
    }
}

