package com.pinturavi.rsql;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import lombok.SneakyThrows;
import org.springframework.data.jpa.domain.Specification;

public class GenericRsqlSpecification<T> implements Specification<T> {

    private final String property;
    private final ComparisonOperator operator;
    private final List<String> arguments;

    public GenericRsqlSpecification(String property,
                                    ComparisonOperator operator,
                                    List<String> arguments) {
        this.property = property;
        this.operator = operator;
        this.arguments = arguments;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

        final List<String> queries = Arrays.stream(property.split("\\."))
                .collect(Collectors.toList());

        if (property.contains(".")) {
            return getPredicate(root,null, root.getJavaType(), new LinkedList<>(queries), builder);
        }

        final List<Object> args = castArguments(root);
        return buildPredicateBasedOnOperator(builder, args, property, root::get);
    }


    @SneakyThrows
    private Predicate getPredicate(final Root<T> root,
                                   final Join<?, ?> join,
                                   final Class<?> clazz,
                                   final LinkedList<String> fieldNames,
                                   final CriteriaBuilder builder) {

        if (fieldNames.size() == 1) {
            final String fieldName = fieldNames.pollFirst();
            final Class<?> subClass = getSubClass(clazz, fieldName);
            final List<Object> args = castArguments(subClass);
            return buildPredicateBasedOnOperator(builder, args, fieldName, join::get);
        }

        final String fieldName = fieldNames.pollFirst();
        final Class<?> subClass = getSubClass(clazz, fieldName);

        return getPredicate(root, null == join ? root.join(fieldName) : join.join(fieldName), subClass, fieldNames, builder);
    }

    private Predicate buildPredicateBasedOnOperator(final CriteriaBuilder builder,
                                                    final List<Object> args,
                                                    final String fieldName,
                                                    final Function<String, Path<String>> pathGetter) {

        final Object argument = args.get(0);
        switch (Objects.requireNonNull(RsqlSearchOperation.getSimpleOperator(operator))) {
            case EQUAL: {
                if (argument instanceof String) {
                    return builder.like(pathGetter.apply(fieldName), argument.toString().replace('*', '%'));
                } else if (argument == null) {
                    return builder.isNull(pathGetter.apply(fieldName));
                } else {
                    return builder.equal(pathGetter.apply(fieldName), argument);
                }
            }
            case NOT_EQUAL: {
                if (argument instanceof String) {
                    return builder.notLike(pathGetter.apply(fieldName), argument.toString().replace('*', '%'));
                } else if (argument == null) {
                    return builder.isNotNull(pathGetter.apply(fieldName));
                } else {
                    return builder.notEqual(pathGetter.apply(fieldName), argument);
                }
            }
            case GREATER_THAN: {
                return builder.greaterThan(pathGetter.apply(fieldName), argument.toString());
            }
            case GREATER_THAN_OR_EQUAL: {
                return builder.greaterThanOrEqualTo(pathGetter.apply(fieldName), argument.toString());
            }
            case LESS_THAN: {
                return builder.lessThan(pathGetter.apply(fieldName), argument.toString());
            }
            case LESS_THAN_OR_EQUAL: {
                return builder.lessThanOrEqualTo(pathGetter.apply(fieldName), argument.toString());
            }
            case IN:
                return pathGetter.apply(fieldName).in(args);
            case NOT_IN:
                return builder.not(pathGetter.apply(fieldName).in(args));
            default:
                return null;
        }
    }

    @SneakyThrows
    private static <T> Class<?> getSubClass(final Class<? extends T> javaType,
                                            final String fieldName) {

        if (javaType.getDeclaredField(fieldName).getType() == List.class) {
           return  Class.forName(((ParameterizedType) javaType.getDeclaredField(fieldName).getGenericType()).getActualTypeArguments()[0].getTypeName());
        } else {
             return getaClass(javaType, fieldName);
        }

    }

    @SneakyThrows
    private static Class<?> getaClass(final Class<?> clazz, final String fieldName) {

        try {
            return clazz.getDeclaredField(fieldName).getType();
        } catch (NoSuchFieldException e) {
            return clazz.getSuperclass().getDeclaredField(fieldName).getType();
        }
    }

    private List<Object> castArguments(final Root<T> root) {

        Class<? extends Object> type = root.get(property).getJavaType();

        return arguments.stream()
                .map(arg -> getSerializable(arg, type))
                .collect(Collectors.toList());
    }

    private List<Object> castArguments(final Class<?> type) {

        return arguments.stream()
                .map(arg -> getSerializable(arg, type))
                .collect(Collectors.toList());
    }

    private static Serializable getSerializable(final String arg, Class<?> type) {

        if (type.equals(Integer.class)) {
            return Integer.parseInt(arg);
        } else if (type.equals(Long.class)) {
            return Long.parseLong(arg);
        } else if (type.equals(Boolean.class)) {
            return Boolean.parseBoolean(arg);
        } else {
            return arg;
        }
    }


}
