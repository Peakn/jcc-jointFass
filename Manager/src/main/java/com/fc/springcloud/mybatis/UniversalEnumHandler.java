package com.fc.springcloud.mybatis;

import com.fc.springcloud.enums.BaseEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 枚举转换类
 *
 * @param <E>
 * @author zhangjie
 * @date 2020-09-23 16:06
 */
@MappedTypes(BaseEnum.class)
public class UniversalEnumHandler<E extends BaseEnum> extends BaseTypeHandler<E> {
    private Class<E> type;
    private Map<Integer, E> enumMap;

    public UniversalEnumHandler() {
    }

    public UniversalEnumHandler(Class<E> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.type = type;
        E[] enums = type.getEnumConstants();
        //配置到 <typeHandler> 初始化时，这里的 type 只是一个接口，并不是枚举，所以要特殊判断
        //下面除了第一个 setNonNullParameter 赋值不需要 enumMap，其他 3 个都需要，
        //由于正常情况下实体类不会使用 LabelValue 接口类型，所以这里没有对 null 进行判断
        if (enums != null) {
            this.enumMap = new HashMap<>(enums.length);
            for (E anEnum : enums) {
                this.enumMap.put(anEnum.getValue(), anEnum);
            }
        }
    }

    /**
     * 用于定义设置参数时，该如何把Java类型的参数转换为对应的数据库类型
     *
     * @param ps
     * @param i
     * @param parameter
     * @param jdbcType
     * @throws SQLException
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        //BaseTypeHandler已经帮我们做了parameter的null判断
        ps.setInt(i, parameter.getValue());
    }

    /**
     * 用于定义通过字段名称获取字段数据时，如何把数据库类型转换为对应的Java类型
     *
     * @param rs
     * @param columnName
     * @return
     * @throws SQLException
     */
    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        //根据数据库存储类型决定获取类型，本例子中数据库中存放int类型
        Integer i = rs.getInt(columnName);
        if (rs.wasNull()) {
            return null;
        } else {
            return locateEnumStatus(i);
        }
    }

    /**
     * 用于定义通过字段索引获取字段数据时，如何把数据库类型转换为对应的Java类型
     *
     * @param rs
     * @param columnIndex
     * @return
     * @throws SQLException
     */
    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Integer i = rs.getInt(columnIndex);
        if (rs.wasNull()) {
            return null;
        } else {
            return locateEnumStatus(i);
        }
    }

    /**
     * 用定义调用存储过程后，如何把数据库类型转换为对应的Java类型
     *
     * @param cs
     * @param columnIndex
     * @return
     * @throws SQLException
     */
    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Integer i = cs.getInt(columnIndex);
        if (cs.wasNull()) {
            return null;
        } else {
            return locateEnumStatus(i);
        }
    }

    /**
     * 获取枚举的子类
     *
     * @param value 数据库中存储的自定义value属性
     * @return value对应的枚举类
     */
    private E locateEnumStatus(Integer value) {
        try {
            return enumMap.get(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot convert " + value + " to " + type.getSimpleName() + " by ordinal value.", ex);
        }
    }
}