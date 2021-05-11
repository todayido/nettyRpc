import com.alibaba.fastjson.JSON;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Test {

    @Autowired
    RestHighLevelClient restHighLevelClient;

    @org.junit.Test
    public void test() throws IOException {

//        SearchResponse searchResponse = null;
//        SearchRequest searchRequest = new SearchRequest("test-001");
//        searchRequest.source();
//
//        searchResponse = restHighLevelClient.search(searchRequest);
//        searchResponse.getHits().getHits();

        List<String> list = Arrays.asList("a,b,c", "1,2,3");

//        //将每个元素转成一个新的且不带逗号的元素
//        Stream<String> s1 = list.stream().map(s -> s.replaceAll(",", ""));
//        s1.forEach(System.out::println); // abc  123

//        Stream<String> s3 = list.stream().flatMap(s -> {
//            //将每个元素转换成一个stream
//            String[] split = s.split(",");
//            Stream<String> s2 = Arrays.stream(split);
//            return s2;
//        });
//        s3.forEach(System.out::println); // a b c 1 2 3

        Function<String, String> function = a -> {
            a += "=======";
            return a;
        };
        list.stream().map(function).forEach(System.out::println);

        list.stream().map(a->{a+="123"+a;
            return a;}).forEach(System.out::println);

        Comparator<? super Integer> comparator = (a,b)->{
            return a - b;};

        List<Integer> list2 = Arrays.asList(3,5, 4, 3, 2, 9, 0);
        list2.stream().sorted(comparator).collect(Collectors.toList()).forEach(System.out::print);
        System.out.println(JSON.toJSON(list2));


        List<Integer> sortList1 = list2.stream().sorted((a, b) -> {
            return a - b;
        }).collect(Collectors.toList());

        System.out.println(sortList1);

        List<Integer> collect = sortList1.stream().map(a -> a = a + 1).collect(Collectors.toList());
        System.out.println(collect);

    }
}
