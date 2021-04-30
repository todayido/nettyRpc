import org.elasticsearch.action.search.SearchResponse;

import java.util.List;
import java.util.Map;

public interface EsDao {
    /**
     * 分页查询
     * @param index 索引名称
     * @param pageSize 页的大小
     * @param pageNum 第几页
     * @return
     */
    public SearchResponse getAllRowsBySearchAfter(String index, Integer pageSize, Integer pageNum);

    SearchResponse getAllRows(String index);

    public SearchResponse getAllRowsByFromSize(String index, Integer pageSize, Integer pageNum);

    SearchResponse getAllRowsByScrollScan(String index, Integer pageSize, Integer pageNum);

    List<Map<String, Object>> getAllRowsByScroll(String indexName, String column, String value);

    public List<Map<String, Object>> getAllRowsByScroll(String index);
    /**
     * 统计每个index下的document总数。
     * count API的命令：GET /index_name/type_name/_count
     * @param indexes 查询一个或多个index
     * @return
     */
    public long totalCount(String...indexes);
    /**
     * 查询某条document是否存在。
     * @param index 索引名称
     * @param id _id字段对应的主键值。
     * @return true存在，false不存在。
     */
    public boolean docIsExist(String index,String id);
    /**
     * 新增一条document。
     * @param index 索引名称
     * @param kvs 各个字段名称（key）和对应的值（value）
     * @return 200/201表示新增成功；其他如400表示新增失败。
     */
    public int insertDoc(String index,Map<String, Object> kvs);

    /**
     * 删除一条document。
     * @param index 索引名称
     * @param id _id字段对应的主键值。
     * @return 200/201表示删除成功；其他如400表示删除失败。
     */
    public int deleteDoc(String index,String id);

    /**
     * 更新一条document
     * @param index 索引名称
     * @param id _id字段对应的主键值。
     * @param kvs 各个字段名称（key）和对应的值（value）
     * @return 200/201表示更新成功；其他如400表示更新失败。
     */
    public int updateDoc(String index,String id,Map<String, Object> kvs);
    /**
     * es使用率 = cluster store(size_in_bytes) / (所有节点的磁盘可用空间 + size_in_bytes)
     * size_in_bytes: "_all":{"total":{"store":{"size_in_bytes": ***}}}
     * 某个节点的磁盘可用空间：（节点状态）"fs":{"total":{"available_in_bytes":***}}
     * @return
     */
    public double usedRate();
    /**
     * 获取某个cluster下所有的index名称列表。
     * 命令：_cluster/state/routing_table
     * @param clusterName 集群名称
     * @return
     */
    public Map<String, List<String>> getClusterIndexes(String clusterName);

    /**
     * 获取某个index下所有的type名称列表。
     * _type字段从elasticsearch 6.0.0版本开始过时，7.0版本后不再使用。
     * @return
     */
    public Map<String,List<String>> getIndexTypes(String clusterName);
    /**
     * @return 全部已使用存储空间的大小
     */
//    public double storeSizeOfMB();
    /**
     * @param index 需要查询的索引名称
     * @return （某个索引下）已使用存储空间的大小
     */
//    public double storeSizeOfMB(String index);
    /**
     * 暂行：1.统计某个集群（相当于数据库）下所有index（相当于表）的数量。默认集群时elasticsearch。
     * 搁置：2.统计某个index（相当于数据库）下所有type（相当于表）的数量。
     * @return 表的数量
     */
    public int countTables();

    /**
     * 获取某个集群（相当于数据库）下所有的indice（相当于表）的名称列表。
     * @return
     */
    public List<String> getTablenamesOfDB();
    /**
     * 按任意字段进行模糊查询
     * @param indexName 索引名
     * @param fieldName 字段名
     * @param fieldValue 字段模糊值
     */
    public SearchResponse queryByRandomField(String indexName, String fieldName, String fieldValue,
                                             int pageSize, int pageNum);

    /**
     * @param indexName 索引名
     * @param fieldName 字段名
     * @param fieldValue 字段模糊值
     * @return 模糊查询时返回的记录总数
     */
    public long totalCountOfFuzzyQuery(String indexName,String fieldName,String fieldValue);
    /**
     * 获取 某个索引的所有字段名
     * @param indexName 索引的名称。例如：table1
     * @return map<k,v> k:字段名，v:空字符串
     */
    public Map<String,Object> getColumnNames(String indexName);
}
