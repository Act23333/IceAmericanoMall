//package org.noLazy.common.convert;
//
//import java.util.Collection;
//
///**
// * MapStruct 映射器标记接口，用于在 Spring 容器中按类型查找
// * @param <S> 源类型
// * @param <T> 目标类型
// */
//
///**
// * @Mapper(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
// * public interface UserMapper {
// *     List<UserDTO> mapList(List<User> users);
// * }
// * @param <S>
// * @param <T>
// */
//public interface IMapper<S, T> {
//
//    /**
//     *
//     * @param source 原对象
//     * @return 转换为目标类型的独享
//     * eg:
//     *
//     * @Override
//     * @Mapping(source = "id", target = "userId")
//     * @Mapping(source = "name", target = "userName")
//     * UserDTO map(User user);
//     */
//    T map(S source);
//
//
//    /**
//     *
//     * @param source 原类型集合
//     * @return 转换为目标类型的对象集合
//     * eg:
//     * @Override
//     * public List<UserDTO> mapList(List<User> source) {
//     *     if (source == null) return null;
//     *     List<UserDTO> list = new ArrayList<>(source.size());
//     *     for (User user : source) {
//     *         list.add(map(user));  // 调用您定义的 map 方法
//     *     }
//     *     return list;
//     * }
//     */
//    Collection<T> mapList(Collection<S> source);
//}