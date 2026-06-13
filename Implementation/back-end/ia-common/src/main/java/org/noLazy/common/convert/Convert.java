package org.noLazy.common.convert;

/**
 * @ClassName: Convert
 * @Description: 对原对象进行计算，并设置到目标对象中
 * @Author: noLazy
 * @Date: 2026/3/21 16:23
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.utils
 */

@FunctionalInterface
public interface Convert<R, T> {
    void convert(R origin, T target);
}
