import com.alibaba.fastjson.JSON;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @org.junit.Test
    public void mapStream(){
        List<Staff> staff = Arrays.asList(
                new Staff("A", 30, "AA"),
                new Staff("B", 27, "BB"),
                new Staff("C", 33, "VV"),
                new Staff("D", 30, "DD"),
                new Staff("E", 31, "EE")
        );

        // convert inside the map() method directly.
        List<StaffPublic> result = staff.stream().map(temp -> {
            StaffPublic.StaffPublicBuilder builder = StaffPublic.builder();
            builder.name(temp.getName()).age(temp.getAge());
            builder.extra(("B".equals(temp.getName())?"":"this field is for B only!"));
            return builder.build();
        }).collect(Collectors.toList());

        // result: [{"age":30,"extra":"AA","name":"A"},{"age":27,"extra":"BB","name":"B"},{"age":33,"extra":"VV","name":"C"},{"age":30,"extra":"DD","name":"D"},{"age":31,"extra":"EE","name":"E"}]
        System.out.println(JSON.toJSONString(staff));
        // result: [{"age":30,"extra":"this field is for B only!","name":"A"},{"age":27,"extra":"","name":"B"},{"age":33,"extra":"this field is for B only!","name":"C"},{"age":30,"extra":"this field is for B only!","name":"D"},{"age":31,"extra":"this field is for B only!","name":"E"}]
        System.out.println(JSON.toJSONString(result));

        // result 'A,B,C,D,E'
        String collect = staff.stream().map(Staff::getName).collect(Collectors.joining(",", "'","'"));
        System.out.println(collect);

        Map<Integer, List<Staff>> integerListMap = staff.stream().collect(Collectors.groupingBy(Staff::getAge));
        /**
         * 分组结果
         * {
         * 33:[{"age":33,"extra":"VV","name":"C"}],
         * 27:[{"age":27,"extra":"BB","name":"B"}],
         * 30:[{"age":30,"extra":"AA","name":"A"},{"age":30,"extra":"DD","name":"D"}],
         * 31:[{"age":31,"extra":"EE","name":"E"}]
         * }
         */
        System.out.println(JSON.toJSONString(integerListMap));

        /**
         * 分区结果
         * {
         * false:[{"age":30,"extra":"AA","name":"A"},{"age":27,"extra":"BB","name":"B"},{"age":30,"extra":"DD","name":"D"}],
         * true:[{"age":33,"extra":"VV","name":"C"},{"age":31,"extra":"EE","name":"E"}]
         * }
         */
        Map<Boolean, List<Staff>> booleanListMap = staff.stream().collect(Collectors.partitioningBy(s -> s.getAge() > 30));
        System.out.println(JSON.toJSONString(booleanListMap));

        Staff staff1 = staff.stream().collect(Collectors.minBy((a, b) -> a.getAge() - b.getAge())).get();
        System.out.println(JSON.toJSONString(staff1));


        /**
         * reducing  是一个收集器（操作），从字面意义上可以理解为“减少操作”：输入多个元素，在一定的操作后，元素减少。
         * Staff(name=B, age=27, extra=BB)
         */
        staff.stream().reduce((a, b) -> {
            return a.getAge() < b.getAge() ? a : b;
        }).stream().collect(Collectors.toList()).forEach(System.out::println);

        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        System.out.println(list.stream().reduce((a, b) -> a + b).get());// return 28


        Stream<String> s1 = Stream.of("aa", "ab", "c", "ad","b");
        Predicate<String> predicate = t -> t.contains("a");
        System.out.println(s1.parallel().collect(() -> new ArrayList<String>(),
                (array, s) -> {
                    if (predicate.test(s)) array.add(s);
                },
                (array1, array2) -> array1.addAll(array2)));// return [aa, ab, ad]

        s1 = Stream.of("aa", "ab", "c", "ad","b");
        List<String> collect1 = s1.filter(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.contains("b");
            }
        }).collect(Collectors.toList());
        System.out.println(collect1);// return [ab, b]

    }
}
