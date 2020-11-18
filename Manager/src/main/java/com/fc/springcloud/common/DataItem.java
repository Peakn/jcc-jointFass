package com.fc.springcloud.common;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author wangqi
 */
public class DataItem extends HashMap<String, Object> {

    private static final long serialVersionUID = -891914279088925241L;

    public DataItem() {

    }

    public DataItem(Object bean) {
        this(bean, new String[]{});
    }

    public DataItem(Object bean, String... ignoreProperties) {
        describe(bean, ignoreProperties);
    }

    private void putMap(Map<String, Object> map) {
        if (null == map) {
            return;
        } else {
            map.forEach((key, value) ->
                    super.put(key, value)
            );
        }
    }

    private void describe(Object bean, String... ignoreProperties) {
        if (bean == null) {
            return;
        } else if (bean instanceof Map) {
            putMap((Map) bean);
            return;
        }
        try {
            for (Entry<String, Object> entry : PropertyUtils.describe(bean).entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.equals("class") || ignoreProperties != null && ArrayUtils.contains(ignoreProperties, key)) {
                    continue;
                }
                super.put(key, val);
            }

        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {

        }
    }

}
