package org.birdbro.common.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用树构建工具（收敛 manage/setting 各 ServiceImpl 里复制的 8 份建树算法）
 *
 * <p>两种口径，与原私有实现逐位等价：
 * <ul>
 * <li>{@link #buildTree}：Map 索引 O(n) 建树（OrgDep/RoleMenu/ExamBody 口径）。
 *     根 = rootParentValue.equals(pid)；父不在集合内的孤儿节点丢弃；叶子 children 保持 null。</li>
 * <li>{@link #buildTreeRecursive}：递归建树（DictMenu/TempReport 口径）。
 *     命中谓词的节点入选；children 恒置（叶子为空表）；谓词里的拆箱/NPE 行为由调用方原样保留。</li>
 * </ul>
 *
 * @author birdbro
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * Map 索引 O(n) 建树：单次遍历建立父子关系（原 buildTreeOptimized/buildMenuTreeOptimized 口径）。
     * 节点顺序按入参集合顺序保持；同 id 重复节点保留首个（等价 toMap 的 (v1,v2)→v1）。
     *
     * @param nodes           节点集合（平铺）
     * @param idGetter        节点 id 提取器
     * @param parentIdGetter  父 id 提取器
     * @param rootParentValue 根层父 id（如 0）
     * @param childrenGetter  children getter（判空延迟初始化用）
     * @param childrenSetter  children setter
     * @param <T>             节点类型
     * @param <K>             id 类型
     * @return 根节点列表
     */
    public static <T, K> List<T> buildTree(Collection<? extends T> nodes,
                                           Function<T, K> idGetter,
                                           Function<T, K> parentIdGetter,
                                           K rootParentValue,
                                           Function<T, List<T>> childrenGetter,
                                           BiConsumer<T, List<T>> childrenSetter) {
        Map<K, T> nodeMap = new HashMap<>(Math.max(16, nodes.size() * 2));
        for (T node : nodes) {
            nodeMap.putIfAbsent(idGetter.apply(node), node);
        }
        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            K pid = parentIdGetter.apply(node);
            if (rootParentValue.equals(pid)) {
                roots.add(node);
            } else {
                T parent = nodeMap.get(pid);
                if (parent != null) {
                    List<T> children = childrenGetter.apply(parent);
                    if (children == null) {
                        children = new ArrayList<>();
                        childrenSetter.accept(parent, children);
                    }
                    children.add(node);
                }
            }
        }
        return roots;
    }

    /**
     * 递归建树：命中 matches(parentValue, node) 的节点入选本层，children 恒置（叶子为空表）
     * （原 DictMenu/TempReport 递归口径）。nodeDecorator 可选，在设置 children 前回调（如禁用标记）。
     * 谓词的拆箱行为（如 upId 为 null 时 NPE）由调用方在 matches 内原样表达，工具不做兜底。
     *
     * @param nodes          节点集合（平铺）
     * @param parentValue    当前层父 id（顶层传根值，如 0）
     * @param matches        (当前层父 id, 节点) → 是否归属该父
     * @param idGetter       节点 id 提取器（下一层 parentValue 来源）
     * @param nodeDecorator  入选节点回调（可为 null），在 children 赋值前执行
     * @param childrenSetter children setter
     * @param <T>            节点类型
     * @param <K>            id 类型
     * @return 当前层节点列表
     */
    public static <T, K> List<T> buildTreeRecursive(Collection<? extends T> nodes,
                                                    K parentValue,
                                                    BiPredicate<K, T> matches,
                                                    Function<T, K> idGetter,
                                                    Consumer<T> nodeDecorator,
                                                    BiConsumer<T, List<T>> childrenSetter) {
        List<T> level = new ArrayList<>();
        for (T node : nodes) {
            if (!matches.test(parentValue, node)) {
                continue;
            }
            if (nodeDecorator != null) {
                nodeDecorator.accept(node);
            }
            childrenSetter.accept(node,
                    buildTreeRecursive(nodes, idGetter.apply(node), matches, idGetter, nodeDecorator, childrenSetter));
            level.add(node);
        }
        return level;
    }
}
