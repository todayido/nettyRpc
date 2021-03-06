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
     * ????????????
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
     * JDBC??????elasticsearch???http port=9200
     * jdbc:es://[[http|https]://][host[:port]]/[prefix]<[?[option=value]&]
     * e.g. jdbc:es://http://server:3456/?timezone=UTC&page.size=250
     * ???????????????SSL
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
     * ?????? elasticsearch ????????? index??????
     */
    private RestHighLevelClient connectToES()  {
        try {
            String esIP = dataBaseDTO.getIp();
            //http port = 9200
            String esPort = dataBaseDTO.getHost();
            //?????????
            //String index = dataBaseDTO.getDbName();
            //TODO: ????????????????????????????????????cluster???????????????
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
            //?????????????????????????????????
            if(hits1 == null || hits1.length < pageSize){
                return resp;
            }
            if(pageNum > 1){
                String scrollId = resp.getScrollId();
                for(int i=1;i<pageNum;i++){
                    //??????scroll id????????????
                    SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                    scrollRequest.scroll(TimeValue.timeValueSeconds(60));
                    resp = restClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                    SearchHit[] hits2 = resp.getHits().getHits();
                    //?????????????????????????????????
                    if(hits2 == null || hits2.length < pageSize){
                        break;
                    }
                    scrollId = resp.getScrollId();
                }
                //????????????
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
                    Map<String, Object> oneRowData = m.getSourceAsMap();  //sourceAsMap ?????????null
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
            //????????????
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            //???????????????setScrollIds()?????????scrollId????????????
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
            //????????????
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            //???????????????setScrollIds()?????????scrollId????????????
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
        //??????????????????????????????????????????????????????IndexNotFoundException: no such index
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
        //??????????????????????????????????????????????????????IndexNotFoundException: no such index
        SearchResponse resp = null;
        SearchRequest search = new SearchRequest(index);
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            //????????????
            sourceBuilder.from(pageSize*pageNum-pageSize);
            sourceBuilder.size(pageSize);
            sourceBuilder.sort("_id", SortOrder.ASC);
            search.source(sourceBuilder);
            resp = restClient.search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //????????? TransportClient ??????API.
        /*if(indexIsExist(index)) {
            resp = client.prepareSearch(index)
                    .setQuery(QueryBuilders.matchAllQuery())
                    .setFrom(pageSize*pageNum-pageSize).setSize(pageSize)
                    //???????????????????????????????????????????????????
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
        //============NOTE: API?????????---??????high level API
        SearchResponse resp = null;
        Object[] sortValues = null;
        int counter = 0;
        try {
            //TODO:??????-pageNum???????????????????????????
            do{
                //???????????????????????????
                counter += 1;
                SearchRequest search = new SearchRequest(index);
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                //????????????
                sourceBuilder.size(pageSize);
                sourceBuilder.sort("_id", SortOrder.ASC);
                //???????????????????????????search_after?????????
                if(sortValues != null){
                    sourceBuilder.searchAfter(sortValues);
                }
                search.source(sourceBuilder);
                resp = restClient.search(search, RequestOptions.DEFAULT);
                SearchHits hits = resp.getHits();
                //???????????????????????????hitSize = 0
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
        //============NOTE: API?????????---??????low level API
        /*Object tiebreaker  = null; //??????????????????????????????ID
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
                //search_after??????
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
                        //????????????????????????
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
                //sourceAsMap ?????????null
                Map<String, Object> oneRowData = hit.getSourceAsMap();
                if(oneRowData != null){
                    oneRowData.put("_id", hit.getId());
                    //oneRowData.put("_type", hit.getType());
                    //[NOTE:]?????????????????????????????????????????????????????????
                    //[NOTE:]???routing??????????????????????????????
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
                //????????????
                JSONObject settingObj = jo.getJSONObject(index)
                        .getJSONObject("settings")
                        .getJSONObject("index");
                String value = settingObj.getString("max_result_window");
                if(value == null){
                    return maxResult; //????????????10000
                }
                maxResult =   Long.valueOf(value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return maxResult;
    }
    /**
     * ????????????????????????
     * @param index ????????????
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
        //????????? TransportClient ??????API.
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
    //?????????????????????????????????????????????????????????
    @Override
    public int insertDoc(String index, Map<String, Object> jsonMap) {
        try {
            if(jsonMap != null){
                //????????????????????????????????????
                Map<String, Object> columns = getColumnNames(index);
                Set<String> keys = jsonMap.keySet();
                for (String key : keys) {
                    EsFieldType eft = EsFieldType.GEO_POINT;
                    if(eft.getType().equals(columns.get(key))){
                        //?????????????????????????????????
                        Object transferedField = eft.getTransferedField(jsonMap.get(key).toString());
                        if(transferedField==null){
                            return 0;
                        }
                        jsonMap.put(key, transferedField);
                    }
                }
                //?????????id??????id??????????????????
                IndexRequest indexRequest = new IndexRequest(index).source(jsonMap);
                //opType must be 'create' or 'index'.
                // optype=index???????????????ID?????????document?????????????????????????????????
                //NOTE: ?????????????????????id????????????????????????????????????????????????id???????????????
                indexRequest.opType(DocWriteRequest.OpType.INDEX);
                //indexRequest.timeout(TimeValue.timeValueSeconds(1));
                //???????????????????????????
                indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                /*
                 *??????????????????????????????????????????????????????????????????????????????
                 *Elasticsearch exception [type=mapper_parsing_exception,
                 * reason=failed to parse field [location] of type [geo_point]]]
                 */
                IndexResponse indexResponse = restClient.index(indexRequest, RequestOptions.DEFAULT);
                int result =  indexResponse.status().getStatus();
                if(result== RestStatus.OK.getStatus() || result == RestStatus.CREATED.getStatus()){
                    return 1; //????????????
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
            // ????????????routing,????????????routing
            String routing = getRouting(index, id);
            if(routing != null){
                delRequest.routing(routing);
            }
            //???????????????????????????
            delRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            DeleteResponse delResp = restClient.delete(delRequest, RequestOptions.DEFAULT);
            int result = delResp.status().getStatus();
            if(result==RestStatus.OK.getStatus() || result == RestStatus.CREATED.getStatus()){
                return 1; //????????????
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;//????????????
    }
    @Override
    public int updateDoc(String index, String id, Map<String, Object> kvs) {
        try {
            //????????????????????????????????????
            Map<String, Object> columns = getColumnNames(index);
            Set<String> keys = kvs.keySet();
            for (String key : keys) {
                EsFieldType eft = EsFieldType.GEO_POINT;
                if(eft.getType().equals(columns.get(key))){
                    //?????????????????????????????????
                    Object transferedField = eft.getTransferedField(kvs.get(key).toString());
                    if(transferedField==null){
                        return 0;
                    }
                    kvs.put(key, transferedField);
                }
            }
            UpdateRequest updateRequest = new UpdateRequest(index, id);
            /*
             * ?????????ElasticsearchStatusException[Elasticsearch exception [type=document_missing_exception
             * ????????????routing,????????????routing
             */
            String routing = getRouting(index, id);
            if(routing != null){
                updateRequest.routing(routing);
            }
            updateRequest.doc(kvs);
            //NOTE:??????document????????????????????????????????????
            UpdateResponse updateResp = restClient.update(updateRequest, RequestOptions.DEFAULT);
            //???????????????????????????
            updateResp.setForcedRefresh(true);
            int result = updateResp.status().getStatus();
            if(result==RestStatus.OK.getStatus() || result == RestStatus.CREATED.getStatus()){
                return 1; //????????????
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
            //?????????????????????????????????search?????????
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
        //es????????? = cluster store(size_in_bytes) / (????????????????????????????????? + size_in_bytes)
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
                //??????????????????????????????????????????????????????????????????index???
                long totalIndexSizes = jo.getJSONObject("indices")
                        .getJSONObject("store")
                        .getLongValue("size_in_bytes");
                //????????????????????????????????????????????????
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
//                    //??????????????????????????????????????????????????????????????????index???
//                    long bytes = jo.getJSONObject("_all")
//                            .getJSONObject("total")
//                            .getJSONObject("store")
//                            .getLongValue("size_in_bytes");
//                    return FileSizeUtil.valueOf((double)bytes, unit);
//                }else{
//                    //????????????????????????????????????????????????
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
//                    //????????????????????????????????????????????????
//                    long bytes = 0L;
//                    for (String index : indices) {
//                        //????????????????????????
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
        //??????????????????elasticsearch
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
                //???????????????indices
                JSONObject indices = jo.getJSONObject("indices");
                nameList.addAll(indices.keySet());
            }
            //????????????????????????index
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
            //????????????
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
        //??????????????????
        String fieldType = getFieldType(indexName, fieldName);
        if("text".equals(fieldType) || "keyword".equals(fieldType)){
            //???*?????????wildcardQuery???????????????????????????
            //???????????????fuzzy???wildcard: ?????????text???keyword???????????????
            //text?????????????????????fuzzy???????????????keyword???????????????fuzzy???????????????
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
            //???????????????fuzzy???wildcard
            setQuerySearchBuilder(sourceBuilder,indexName,fieldName,fieldValue);
            //???????????????????????????search?????????
            CountRequest countRequest = new CountRequest(new String[]{indexName},sourceBuilder);
            CountResponse countResponse = restClient.count(countRequest, RequestOptions.DEFAULT);
            counter = countResponse.getCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return counter;
    }

    //??????????????????
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
     * ??????mapping??????
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
                        //????????????????????????
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
     * ????????????ID????????????document????????????routing??????
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
                        //???routing?????????????????????
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
