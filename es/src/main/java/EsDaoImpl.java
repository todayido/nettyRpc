import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class EsDaoImpl implements EsDao{
    RestHighLevelClient restClient = null;
    DataBaseDTO dataBaseDTO = null;

    public EsDaoImpl(DataBaseDTO dbd) {
        try {
            dataBaseDTO = dbd;
            this.restClient = connectToES();
            //initClient();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public RestHighLevelClient getRestClient() {
        return restClient;
    }
    public void setRestClient(RestHighLevelClient restClient) {
        this.restClient = restClient;
    }
    /**
     * 关闭连接
     */
    public void close(){
        try{
            if(this.restClient != null){
                restClient.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    public boolean connected(){
        try {
            if(getRestClient() != null && getRestClient().ping(RequestOptions.DEFAULT)){
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    /**
     * JDBC连接elasticsearch。http port=9200
     * jdbc:es://[[http|https]://][host[:port]]/[prefix]<[?[option=value]&]
     * e.g. jdbc:es://http://server:3456/?timezone=UTC&page.size=250
     * 安全通信：SSL
     * @return
     * @throws SQLException
     */
    /*private Connection connectToES_abandon() throws SQLException {
        if(conn == null){
            if(dataBaseDTO != null){
                String url = "jdbc:es://"+dataBaseDTO.getIp()+":9200";
                System.out.println(url);
                EsDataSource ds = new EsDataSource();
                ds.setUrl(url);
                Properties props = new Properties();
                *//*props.put("user", "test_admin");
                props.put("password", "x-pack-test-password");*//*
                ds.setProperties(props);
                return ds.getConnection();
                //return DriverManager.getConnection(url, props);
            }
        }
        return conn;
    }*/

    /**
     * 连接 elasticsearch 并获取 index信息
     */
    private RestHighLevelClient connectToES()  {
        try {
            String esIP = dataBaseDTO.getIp();
            //http port = 9200
            String esPort = dataBaseDTO.getHost();
            //索引。
            //String index = dataBaseDTO.getDbName();
            //TODO: 用户名、密码做校验。目前cluster是默认的。
            return new RestHighLevelClient(
                    RestClient.builder(new HttpHost(esIP, Integer.valueOf(esPort), "http")));
            //flag = indexIsExist(index);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public SearchResponse getAllRowsByScrollScan(String index, Integer pageSize, Integer pageNum) {
        return getAllRowsByScrollScan(null,index,pageSize,pageNum);
    }
    private SearchResponse getAllRowsByScrollScan(SearchSourceBuilder sourceBuilder, String index,
                                                  Integer pageSize, Integer pageNum) {
        if(pageSize==null || pageSize<1){
            pageSize = 10;
        }
        if(pageNum==null || pageNum<1){
            pageNum = 1;
        }
        SearchRequest search = new SearchRequest(index);
        SearchResponse resp = null;
        if(sourceBuilder == null){
            sourceBuilder = new SearchSourceBuilder();
        }
        sourceBuilder.size(pageSize);
        sourceBuilder.sort("_id", SortOrder.ASC);
        search.source(sourceBuilder)
                .searchType(SearchType.QUERY_THEN_FETCH)
                .scroll(TimeValue.timeValueSeconds(60));
        try {
            resp = restClient.search(search, RequestOptions.DEFAULT);
            SearchHit[] hits1 = resp.getHits().getHits();
            //查询结果太少，直接返回
            if(hits1 == null || hits1.length < pageSize){
                return resp;
            }
            if(pageNum > 1){
                String scrollId = resp.getScrollId();
                for(int i=1;i<pageNum;i++){
                    //利用scroll id继续查询
                    SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                    scrollRequest.scroll(TimeValue.timeValueSeconds(60));
                    resp = restClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                    SearchHit[] hits2 = resp.getHits().getHits();
                    //查询结果太少，直接返回
                    if(hits2 == null || hits2.length < pageSize){
                        break;
                    }
                    scrollId = resp.getScrollId();
                }
                //清除滚屏
                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(scrollId);
                restClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resp;
    }

    @Override
    public List<Map<String, Object>> getAllRowsByScroll(String indexName, String column, String value) {
        List<Map<String, Object>> collect = new ArrayList<>();
        final Scroll scroll = new Scroll(TimeValue.timeValueSeconds(60));
        SearchResponse resp = null;
        SearchRequest search = new SearchRequest(indexName);
        search.scroll(scroll);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();


        try {
            setQuerySearchBuilder(sourceBuilder,indexName,column,value);
            sourceBuilder.size(100);
            sourceBuilder.sort("_id", SortOrder.ASC);
            search.source(sourceBuilder)
                    .searchType(SearchType.QUERY_THEN_FETCH)
                    .scroll(TimeValue.timeValueSeconds(60));
            resp = restClient.search(search, RequestOptions.DEFAULT);
            assert resp != null;
            String scrollId;
            int count = 0;
            do {
                count++;
                System.out.println("=================="+count);

                collect.addAll(Arrays.stream(resp.getHits().getHits()).map(m->{
                    Map<String, Object> oneRowData = m.getSourceAsMap();  //sourceAsMap 可能为null
                    if(oneRowData != null){
                        oneRowData.put("_id", m.getId());
                        //oneRowData.put("_type", hit.getType());
                    }
                    return oneRowData;
                }).collect(Collectors.toList()));

                scrollId = resp.getScrollId();
                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                scrollRequest.scroll(scroll);
                resp = restClient.scroll(scrollRequest, RequestOptions.DEFAULT);
            } while (resp.getHits().getHits().length != 0);
            //清除滚屏
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            //也可以选择setScrollIds()将多个scrollId一起使用
            clearScrollRequest.addScrollId(scrollId);
            restClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            if (collect.size() == 0 || collect == null) {
                return  null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return collect;
    }

    @Override
    public   List<Map<String, Object>>  getAllRowsByScroll(String index) {
        List<Map<String, Object>> collect = new ArrayList<>();
        final Scroll scroll = new Scroll(TimeValue.timeValueSeconds(60));
        SearchResponse resp = null;
        SearchRequest search = new SearchRequest(index);
        search.scroll(scroll);
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.size(100);
            sourceBuilder.sort("_id", SortOrder.ASC);
            search.source(sourceBuilder);
            resp = restClient.search(search, RequestOptions.DEFAULT);
            assert resp != null;
            String scrollId;
            int count = 0;
            do {

                Arrays.stream(resp.getHits().getHits()).forEach(hit->{
                    Map<String,Object> map=hit.getSourceAsMap();
                    map.put("_id",hit.getId());
                    collect.add(map);
                });
                scrollId = resp.getScrollId();
                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                scrollRequest.scroll(scroll);
                resp = restClient.scroll(scrollRequest, RequestOptions.DEFAULT);
            } while (resp.getHits().getHits().length != 0);
            //清除滚屏
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            //也可以选择setScrollIds()将多个scrollId一起使用
            clearScrollRequest.addScrollId(scrollId);
            restClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            if (collect.size() == 0 || collect == null) {
                return  null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return collect;
    }

    @Override
    public SearchResponse getAllRows(String index) {
        //如果不判断索引是否存在，可能会报错：IndexNotFoundException: no such index
        SearchResponse resp = null;
        SearchRequest search = new SearchRequest(index);
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            search.source(sourceBuilder);
            resp = restClient.search(search, RequestOptions.DEFAULT);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return resp;
    }
    @Override
    public SearchResponse getAllRowsByFromSize(String index, Integer pageSize, Integer pageNum) {
        if(pageSize==null || pageSize<1){
            pageSize = 10;
        }
        if(pageNum==null || pageNum<1){
            pageNum = 1;
        }
        //如果不判断索引是否存在，可能会报错：IndexNotFoundException: no such index
        SearchResponse resp = null;
        SearchRequest search = new SearchRequest(index);
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            //分页查询
            sourceBuilder.from(pageSize*pageNum-pageSize);
            sourceBuilder.size(pageSize);
            sourceBuilder.sort("_id", SortOrder.ASC);
            search.source(sourceBuilder);
            resp = restClient.search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //当使用 TransportClient 时的API.
        /*if(indexIsExist(index)) {
            resp = client.prepareSearch(index)
                    .setQuery(QueryBuilders.matchAllQuery())
                    .setFrom(pageSize*pageNum-pageSize).setSize(pageSize)
                    //如果不排序，每次返回的结果是乱序的
                    .addSort("_id", SortOrder.ASC)
                    //.setPostFilter(QueryBuilders.rangeQuery("doc.offset").from(7000).to(10000))
                    .get();
        }*/
        return resp;
    }
    @Override
    public SearchResponse getAllRowsBySearchAfter(String index, Integer pageSize, Integer pageNum) {
        if(pageSize==null || pageSize<1){
            pageSize = 10;
        }
        if(pageNum==null || pageNum<1){
            pageNum = 1;
        }
        //============NOTE: API方式一---使用high level API
        SearchResponse resp = null;
        Object[] sortValues = null;
        int counter = 0;
        try {
            //TODO:问题-pageNum大时，速度非常慢！
            do{
                //计数：当前是第几页
                counter += 1;
                SearchRequest search = new SearchRequest(index);
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                //分页查询
                sourceBuilder.size(pageSize);
                sourceBuilder.sort("_id", SortOrder.ASC);
                //第一次搜索时，没有search_after参数。
                if(sortValues != null){
                    sourceBuilder.searchAfter(sortValues);
                }
                search.source(sourceBuilder);
                resp = restClient.search(search, RequestOptions.DEFAULT);
                SearchHits hits = resp.getHits();
                //当搜索没有结果时，hitSize = 0
                int hitSize= hits.getHits().length;
                if(hitSize == 0){
                    break;
                }
                SearchHit hit = hits.getHits()[hitSize - 1];
                sortValues = hit.getSortValues();
            }while(counter < pageNum);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resp;
        //============NOTE: API方式二---使用low level API
        /*Object tiebreaker  = null; //查询的最后一个结果的ID
        String jsonParam = null;
        try {
            do{
                if(tiebreaker == null){
                    jsonParam = "{\"size\": "+pageSize+
                            ",\"sort\": [{\"_id\": \"desc\"}]}";
                }else{
                    jsonParam = "{\"size\": "+pageSize+"," +
                            "\"search_after\":"+tiebreaker+","+
                            "\"sort\": [{\"_id\": \"desc\"}]}";
                }
                //search_after请求
                Request req = new Request("get", index+"/_search");
                req.setJsonEntity(jsonParam);
                RestClient client = restClient.getLowLevelClient();
                Response resp = client.performRequest(req);
                HttpEntity entity = resp.getEntity();
                if(entity != null){
                    InputStream content = entity.getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                    String line = null;
                    StringBuffer sb = new StringBuffer();
                    while((line = reader.readLine())!=null){
                        sb.append(line);
                    }
                    JSONObject jo = JSON.parseObject(sb.toString());
                    JSONArray jsonArray = jo.getJSONObject("hits").getJSONArray("hits");
                    int dataSize = jsonArray.size();
                    if(dataSize > 0){
                       *//* XContentParser parser = xContentType.xContent().createParser(NamedXContentRegistry.EMPTY,
                                DeprecationHandler.THROW_UNSUPPORTED_OPERATION, jsonArray.toJSONString());*//*
                        //返回的分页结果集
                        Object lastResult = jsonArray.get(jsonArray.size() - 1);
                        if(lastResult instanceof  JSONObject){
                            tiebreaker  = ((JSONObject) lastResult).getJSONArray("sort");
                        }
                    }else{
                        break;
                    }
                }else{
                    break;
                }
            }while(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;*/
    }
    public Map<String,Object> extractResponse(SearchResponse resp){
        Map<String,Object> rowDatas = new HashMap<>();
        List<Map<String,Object>> data = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        if(resp != null){
            SearchHits hits = resp.getHits();
            Iterator<SearchHit> ite = hits.iterator();
            while(ite.hasNext()){
                SearchHit hit = ite.next();
                //sourceAsMap 可能为null
                Map<String, Object> oneRowData = hit.getSourceAsMap();
                if(oneRowData != null){
                    oneRowData.put("_id", hit.getId());
                    //oneRowData.put("_type", hit.getType());
                    //[NOTE:]前端需要显示几何对象的经纬度数值字符串
                    //[NOTE:]有routing路由的，需要加入路由
                }
                fields.addAll(oneRowData.keySet());
                data.add(oneRowData);
            }
        }
        rowDatas.put("data", data);
        rowDatas.put("fields", fields);
        rowDatas.put("pk", "_id");
        return rowDatas;
    }
    private long getMaxresult(String index){
        //ClusterGetSettingsRequest cgsr = new ClusterGetSettingsRequest();
        long maxResult = 10000;
        try {
            Request req = new Request("get", index+"/_settings");
            RestClient client = restClient.getLowLevelClient();
            Response resp = client.performRequest(req);
            HttpEntity entity = resp.getEntity();
            if(entity != null){
                InputStream content = entity.getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                String line = null;
                StringBuffer sb = new StringBuffer();
                while((line = reader.readLine())!=null){
                    sb.append(line);
                }
                JSONObject jo = JSON.parseObject(sb.toString());
                //查询设置
                JSONObject settingObj = jo.getJSONObject(index)
                        .getJSONObject("settings")
                        .getJSONObject("index");
                String value = settingObj.getString("max_result_window");
                if(value == null){
                    return maxResult; //默认大小10000
                }
                maxResult =   Long.valueOf(value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return maxResult;
    }
    /**
     * 判断索引是否存在
     * @param index 索引名称
     * @return boolean
     */
    private boolean isIndexExist(String index){
        try{
            if(!StringUtils.isEmpty(index)){
                GetIndexRequest gir = new GetIndexRequest(index);
                return  restClient.indices().exists(gir, RequestOptions.DEFAULT);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        //当使用 TransportClient 时的API.
        //BasicConfigurator.configure();
        /*AdminClient admin = client.admin();
        IndicesAdminClient indices = admin.indices();
        IndicesExistsRequestBuilder ierb = indices.prepareExists(index);
        ActionFuture<IndicesExistsResponse> future = ierb.execute();
        IndicesExistsResponse resp = future.actionGet();
        if(resp.isExists()){
            System.out.format("index [%s] is exist...\n", index);
            return true;
        }else {
            System.out.format("index [%s] is NOT exist...\n", index);
            return false;
        }*/
        return false;
    }
    //插入数据时，需要注意数据类型是否匹配。
    @Override
    public int insertDoc(String index, Map<String, Object> jsonMap) {
        try {
            if(jsonMap != null){
                //将几何类型的转为特定对象
                Map<String, Object> columns = getColumnNames(index);
                Set<String> keys = jsonMap.keySet();
                for (String key : keys) {
                    EsFieldType eft = EsFieldType.GEO_POINT;
                    if(eft.getType().equals(columns.get(key))){
                        //如果是几何类型，就转换
                        Object transferedField = eft.getTransferedField(jsonMap.get(key).toString());
                        if(transferedField==null){
                            return 0;
                        }
                        jsonMap.put(key, transferedField);
                    }
                }
                //不设置id时，id会自动生成。
                IndexRequest indexRequest = new IndexRequest(index).source(jsonMap);
                //opType must be 'create' or 'index'.
                // optype=index时，如果某ID对应的document已经存在，它将被替换。
                //NOTE: 要么需要判断该id是否存在，已存在则不新增；要么让id自动生成。
                indexRequest.opType(DocWriteRequest.OpType.INDEX);
                //indexRequest.timeout(TimeValue.timeValueSeconds(1));
                //强制刷新，消除延迟
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                /*
                 *几何类型的字段，可能报错：需要按几何对象格式去存储。
                 *Elasticsearch exception [type=mapper_parsing_exception,
                 * reason=failed to parse field [location] of type [geo_point]]]
                 */
                IndexResponse indexResponse = restClient.index(indexRequest, RequestOptions.DEFAULT);
                int result =  indexResponse.status().getStatus();
                if(result== RestStatus.OK.getStatus() || result == RestStatus.CREATED.getStatus()){
                    return 1; //新增成功
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    @Override
    public int deleteDoc(String index, String id) {
        try {
            DeleteRequest delRequest = new DeleteRequest(index,id);
            // 如果存在routing,需要指定routing
            String routing = getRouting(index, id);
            if(routing != null){
                delRequest.routing(routing);
            }
            //强制刷新，消除延迟
            delRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            DeleteResponse delResp = restClient.delete(delRequest, RequestOptions.DEFAULT);
            int result = delResp.status().getStatus();
            if(result==RestStatus.OK.getStatus() || result == RestStatus.CREATED.getStatus()){
                return 1; //删除成功
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;//删除失败
    }
    @Override
    public int updateDoc(String index, String id, Map<String, Object> kvs) {
        try {
            //将几何类型的转为特定对象
            Map<String, Object> columns = getColumnNames(index);
            Set<String> keys = kvs.keySet();
            for (String key : keys) {
                EsFieldType eft = EsFieldType.GEO_POINT;
                if(eft.getType().equals(columns.get(key))){
                    //如果是几何类型，就转换
                    Object transferedField = eft.getTransferedField(kvs.get(key).toString());
                    if(transferedField==null){
                        return 0;
                    }
                    kvs.put(key, transferedField);
                }
            }
            UpdateRequest updateRequest = new UpdateRequest(index, id);
            /*
             * 报错：ElasticsearchStatusException[Elasticsearch exception [type=document_missing_exception
             * 如果存在routing,需要指定routing
             */
            String routing = getRouting(index, id);
            if(routing != null){
                updateRequest.routing(routing);
            }
            updateRequest.doc(kvs);
            //NOTE:查询document，如果不存在，就不更新。
            UpdateResponse updateResp = restClient.update(updateRequest, RequestOptions.DEFAULT);
            //强制刷新，消除延迟
            updateResp.setForcedRefresh(true);
            int result = updateResp.status().getStatus();
            if(result==RestStatus.OK.getStatus() || result == RestStatus.CREATED.getStatus()){
                return 1; //更新成功
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean docIsExist(String index, String id) {
        boolean flag = false;
        try {
            GetRequest getRequest = new GetRequest(index, id);
            GetResponse getResp = restClient.get(getRequest, RequestOptions.DEFAULT);
            flag = getResp.isExists();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            return flag;
        }
    }
    @Override
    public long totalCount(String...indexes) {
        long countNum = 0;
        try {
            //直接统计总数，没有设置search条件。
            CountRequest countRequest = new CountRequest(indexes);
            CountResponse countResp = restClient.count(countRequest, RequestOptions.DEFAULT);
            countNum = countResp.getCount();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            return countNum;
        }
    }

    @Override
    public double usedRate() {
        //es使用率 = cluster store(size_in_bytes) / (所有节点的磁盘可用空间 + size_in_bytes)
        double rate = 0.0;
        try {
            Request req = new Request("get", "_cluster/stats");
            RestClient client = restClient.getLowLevelClient();
            Response resp = client.performRequest(req);
            HttpEntity entity = resp.getEntity();
            if(entity != null){
                InputStream content = entity.getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                String line = null;
                StringBuffer sb = new StringBuffer();
                while((line = reader.readLine())!=null){
                    sb.append(line);
                }
                JSONObject jo = JSON.parseObject(sb.toString());
                //查询整个集群的已用的存储空间大小（包括所有的index）
                long totalIndexSizes = jo.getJSONObject("indices")
                        .getJSONObject("store")
                        .getLongValue("size_in_bytes");
                //查询各个节点上的磁盘可用空间总和
                long totalAvailableFSSizes = jo.getJSONObject("nodes")
                        .getJSONObject("fs")
                        .getLongValue("available_in_bytes");
                System.out.println(totalIndexSizes+"==============="+totalAvailableFSSizes);
                rate = (double)totalIndexSizes / (totalAvailableFSSizes + totalIndexSizes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            return Double.parseDouble(String.format("%.2f",rate*100));
        }
    }

//    @Override
//    public double storeSizeOfDB(SizeUnitEnum unit) {
//        return storeSizeOfDB(null,unit);
//    }
//
//    @Override
//    public double storeSizeOfDB(String index,SizeUnitEnum unit) {
//        try {
//            Request req = new Request("get", "_stats");
//            RestClient client = restClient.getLowLevelClient();
//            Response resp = client.performRequest(req);
//            HttpEntity entity = resp.getEntity();
//            if(entity != null){
//                InputStream content = entity.getContent();
//                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
//                String line = null;
//                StringBuffer sb = new StringBuffer();
//                while((line = reader.readLine())!=null){
//                    sb.append(line);
//                }
//                JSONObject jo = JSON.parseObject(sb.toString());
//                if(StringUtils.isEmpty(index)){
//                    //查询整个集群的已用的存储空间大小（包括所有的index）
//                    long bytes = jo.getJSONObject("_all")
//                            .getJSONObject("total")
//                            .getJSONObject("store")
//                            .getLongValue("size_in_bytes");
//                    return FileSizeUtil.valueOf((double)bytes, unit);
//                }else{
//                    //查询某个索引下已用的存储空间大小
//                    if(isIndexExist(index)){
//                        long bytes = jo.getJSONObject("indices")
//                                .getJSONObject(index)
//                                .getJSONObject("total")
//                                .getJSONObject("store")
//                                .getLongValue("size_in_bytes");
//                        return FileSizeUtil.valueOf((double)bytes, unit);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return 0.0;
//    }
//
//    @Override
//    public double storeSizeOfTbl(String[] indices, SizeUnitEnum unit) {
//        try {
//            if(indices != null ){
//                Request req = new Request("get", "_stats");
//                RestClient client = restClient.getLowLevelClient();
//                Response resp = client.performRequest(req);
//                HttpEntity entity = resp.getEntity();
//                if(entity != null){
//                    InputStream content = entity.getContent();
//                    BufferedReader reader = new BufferedReader(new InputStreamReader(content));
//                    String line = null;
//                    StringBuffer sb = new StringBuffer();
//                    while((line = reader.readLine())!=null){
//                        sb.append(line);
//                    }
//                    JSONObject jo = JSON.parseObject(sb.toString());
//                    JSONObject indiceJO = jo.getJSONObject("indices");
//                    //查询某个索引下已用的存储空间大小
//                    long bytes = 0L;
//                    for (String index : indices) {
//                        //判断索引是否存在
//                        //if(isIndexExist(index)){ }
//                        if(indiceJO.get(index) != null){
//                            bytes += indiceJO
//                                    .getJSONObject(index)
//                                    .getJSONObject("total")
//                                    .getJSONObject("store")
//                                    .getLongValue("size_in_bytes");
//                        }
//                    }
//                    return FileSizeUtil.valueOf((double)bytes, unit);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return 0.0;
//    }

    @Override
    public int countTables() {
        //默认的集群：elasticsearch
        return countIndices("elasticsearch");
    }
    private int countIndices(String clusterName){
        //ClusterStatsRequest req = new ClusterStatsRequest();
        //Request req = new Request("get", "_cluster/stats");
        return getTablenamesOfDB().size();
    }
    @Override
    public List<String> getTablenamesOfDB() {
        List<String> nameList = new ArrayList<>();
        List<String> filteredNameList = new ArrayList<>();
        try {
            Request req = new Request("get", "_stats");
            Response resp = restClient.getLowLevelClient().performRequest(req);
            HttpEntity entity = resp.getEntity();
            if(entity != null){
                InputStream content = entity.getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                String line = null;
                StringBuffer sb = new StringBuffer();
                while((line = reader.readLine())!=null){
                    sb.append(line);
                }
                JSONObject jo = JSON.parseObject(sb.toString());
                //获取所有的indices
                JSONObject indices = jo.getJSONObject("indices");
                nameList.addAll(indices.keySet());
            }
            //过滤掉自动生成的index
            for (String idx : nameList) {
                if(!idx.startsWith(".")){
                    filteredNameList.add(idx);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filteredNameList;
    }

    @Override
    public SearchResponse queryByRandomField(String indexName, String fieldName, String fieldValue,
                                             int pageSize,int pageNum) {
        SearchResponse resp = null;
        try {
           /* SearchRequest search = new SearchRequest(indexName);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            //分页查询
            sourceBuilder.from(pageSize*pageNum-pageSize);
            sourceBuilder.size(pageSize);
            sourceBuilder.sort("_id", SortOrder.ASC);
            setQuerySearchBuilder(sourceBuilder,indexName,fieldName,fieldValue);
            search.source(sourceBuilder);
            resp = restClient.search(search, RequestOptions.DEFAULT);*/
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            setQuerySearchBuilder(sourceBuilder,indexName,fieldName,fieldValue);
            resp = getAllRowsByScrollScan(sourceBuilder, indexName, pageSize, pageNum);
        }catch(Exception e){
            e.printStackTrace();
        }
        return resp;
    }

    private void setQuerySearchBuilder(SearchSourceBuilder sourceBuilder,
                                       String indexName,
                                       String fieldName, String fieldValue) throws Exception{
        String[]numeric = {"long","integer","short","byte","double","float","half_float","scaled_float"};
        List<String> numericTypes = Arrays.asList(numeric);
        //获取字段类型
        String fieldType = getFieldType(indexName, fieldName);
        if("text".equals(fieldType) || "keyword".equals(fieldType)){
            //以*开头的wildcardQuery非常慢，不建议使用
            //模糊查询：fuzzy，wildcard: 只针对text，keyword类型字段。
            //text类型：可以使用fuzzy模糊查询。keyword类型，使用fuzzy查询失效。
            sourceBuilder.query(QueryBuilders.wildcardQuery(fieldName,"*" + fieldValue + "*"));
            //sourceBuilder.query(QueryBuilders.fuzzyQuery(fieldName, fieldValue).fuzziness(Fuzziness.AUTO));
            //sourceBuilder.query(QueryBuilders.matchQuery(fieldName, fieldValue).fuzziness(Fuzziness.AUTO));
        }else if(numericTypes.contains(fieldType)){
//            if(StringUtil.(fieldValue)){
                sourceBuilder.query(QueryBuilders.rangeQuery(fieldName).gte(fieldValue));
//            }
        }else if("geo_point".equals(fieldType)){
            //Geo fields do not support exact searching, use dedicated geo queries instead
            if(fieldValue != null){
                String[] locations = fieldValue.split(",");
                if(locations.length == 4){
//                    double top = StringUtil.isNumeric(locations[0].trim())?Double.valueOf(locations[0].trim()):90;
//                    double left = StringUtil.isNumeric(locations[1].trim())?Double.valueOf(locations[1].trim()): -180;
//                    double bottom = StringUtil.isNumeric(locations[2].trim())?Double.valueOf(locations[2].trim()) :-90;
//                    double right = StringUtil.isNumeric(locations[3].trim())?Double.valueOf(locations[3].trim()): 180;
//                    sourceBuilder.query(QueryBuilders.geoBoundingBoxQuery(fieldName)
//                            .setCorners(top, left, bottom, right));
                }
            }
        }else if("geo_shape".equals(fieldType)){
            //Geo fields do not support exact searching, use dedicated geo queries instead
            if(fieldValue != null){
                String[] locations = fieldValue.split(",");
                if(locations.length == 4){
//                    double top = StringUtil.isNumeric(locations[0].trim())?Double.valueOf(locations[0].trim()):90;
//                    double left = StringUtil.isNumeric(locations[1].trim())?Double.valueOf(locations[1].trim()): -180;
//                    double bottom = StringUtil.isNumeric(locations[2].trim())?Double.valueOf(locations[2].trim()) :-90;
//                    double right = StringUtil.isNumeric(locations[3].trim())?Double.valueOf(locations[3].trim()): 180;
//                    List<Coordinate> coordinates = new CoordinatesBuilder().coordinate(left, top)
//                            .coordinate(right, bottom).build();
//                    GeometryCollectionBuilder gcb = new GeometryCollectionBuilder();
//                    gcb.coordinates(coordinates);
//                    sourceBuilder.query(QueryBuilders.geoWithinQuery(fieldName, gcb));
                }
            }
        }else{
            sourceBuilder.query(QueryBuilders.matchQuery(fieldName, fieldValue));
        }
    }
    @Override
    public long totalCountOfFuzzyQuery(String indexName, String fieldName, String fieldValue) {
        long counter = 0;
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            //模糊查询：fuzzy，wildcard
            setQuerySearchBuilder(sourceBuilder,indexName,fieldName,fieldValue);
            //直接统计总数，设置search条件。
            CountRequest countRequest = new CountRequest(new String[]{indexName},sourceBuilder);
            CountResponse countResponse = restClient.count(countRequest, RequestOptions.DEFAULT);
            counter = countResponse.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return counter;
    }

    //获取字段类型
    private String getFieldType(String indice,String fieldName)throws IOException{
        Map<String, MappingMetaData> mappings = getMappingInfo(indice);
        Map<String, Object> source = mappings.get(indice).getSourceAsMap();
        Object properties = source.get("properties");
        if(properties instanceof LinkedHashMap){
            LinkedHashMap map = (LinkedHashMap)properties;
            Object field = map.get(fieldName);
            if(field instanceof LinkedHashMap){
                LinkedHashMap fieldMap = (LinkedHashMap)field;
                String type = fieldMap.get("type").toString();
                return type;
            }
        }
        return null;
    }
    /**
     * 获取mapping信息
     * @param indice
     * @return
     * @throws IOException
     */
    public Map<String, MappingMetaData> getMappingInfo(String indice) throws IOException{
        GetMappingsRequest gmr = new GetMappingsRequest();
        gmr.indices(indice);
        GetMappingsResponse resp = restClient.indices()
                .getMapping(gmr, RequestOptions.DEFAULT);
        Map<String, MappingMetaData> mappings = resp.mappings();
        return mappings;
    }

    @Override
    public Map<String, Object> getColumnNames(String indexName) {
        Map<String, Object> columnNames = new HashMap<>();
        GetMappingsRequest mappingsRequest = new GetMappingsRequest().indices(indexName);
        try {
            GetMappingsResponse mappingsResponse = restClient.indices()
                    .getMapping(mappingsRequest, RequestOptions.DEFAULT);
            Map<String, MappingMetaData> mappings = mappingsResponse.mappings();
            if(mappings != null){
                MappingMetaData metaData = mappings.get(indexName);
                if(metaData != null){
                    Map<String, Object> sourceAsMap = metaData.getSourceAsMap();//properties
                    if(sourceAsMap != null){
                        Collection<Object> collection = sourceAsMap.values();//Object = map
                        Map<String,Object> tmp = new HashMap<>();
                        Iterator<Object> ite = collection.iterator();
                        while (ite.hasNext()){
                            tmp.putAll((Map<String,Object>)ite.next());
                        }
                        Set<String> fields = tmp.keySet();
                        //提取字段名和类型
                        for (String field : fields) {
                            Map<String,Object> fieldMap = (Map<String,Object>)tmp.get(field);
                            columnNames.put(field, fieldMap.get("type"));
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return columnNames;
    }

    /**
     * 根据索引ID查询对应document，并返回routing内容
     * @param index
     * @param id
     */
    private String getRouting(String index, String id){
        SearchResponse resp = queryByRandomField(index, "_id", id, 1, 1);
        if(resp != null){
            SearchHits hits = resp.getHits();
            Iterator<SearchHit> ite = hits.iterator();
            while(ite.hasNext()){
                SearchHit hit = ite.next();
                DocumentField df = hit.field("_routing");
                if(df != null){
                    List<Object> values = df.getValues();
                    if(values != null){
                        String valStr = values.toString();
                        //将routing转成字符串返回
                        return valStr.substring(1, valStr.length()-1);
                    }
                }
            }
        }
        return null;
    }
    @Override
    public Map<String, List<String>> getClusterIndexes(String clusterName) {
        return null;
    }

    @Override
    public Map<String, List<String>> getIndexTypes(String clusterName) {
        return null;
    }
}
