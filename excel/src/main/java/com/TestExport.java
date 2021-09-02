package com;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;

public class TestExport {

    JTable table;
    JScrollPane scrollPane;
    // 创建表格中的横标题
    String[] titles = { " - ", " - " };
    Object[][] contents = {/* 创建表格中的数据*/ {"00000000", "哈哈哈..."}};

    DefaultTableModel newtablemodel = new DefaultTableModel();

    public TestExport(){
        JFrame mainframe = new JFrame();

//        JButton jButtonInput = new JButton();
//        jButtonInput.setText("请输入查询条件，以空格隔开");
//        jButtonInput.setSize(new Dimension(200, 30));
//        jButtonInput.setLocation(10,10);
//        mainframe.getContentPane().add(jButtonInput, BorderLayout.AFTER_LAST_LINE);

        JTextField textField = new JTextField(50);
        textField.setSize(new Dimension(50, 30));

        JButton search = new JButton("查询");
        search.setSize(new Dimension(200, 30));
//        search.setLocation(220,10);
//        mainframe.getContentPane().add(search,BorderLayout.AFTER_LAST_LINE);

        Panel panel = new Panel();
        panel.add(textField,BorderLayout.AFTER_LAST_LINE);
        panel.add(search,BorderLayout.AFTER_LAST_LINE);
        mainframe.getContentPane().add(panel, BorderLayout.NORTH);


        // 以Names和playerInfo为参数，创建一个表格
        newtablemodel.setColumnIdentifiers(titles);
        table = new JTable(newtablemodel);
        // 设置此表视图的首选大小
        table.setPreferredScrollableViewportSize(new Dimension(1200, 650));
        table.setLocation(50, 100);
        // 将表格加入到滚动条组件中
        scrollPane = new JScrollPane(table);
        scrollPane.setLocation(50, 100);
        mainframe.getContentPane().add(scrollPane, BorderLayout.AFTER_LAST_LINE);
        // 再将滚动条组件添加到中间容器中
        mainframe.setTitle("表格测试窗口");
        mainframe.pack();
        mainframe.setVisible(true);
        mainframe.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        search.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String str = textField.getText();//获取输入内容
                    //判断是否输入了
                    if (str.equals("")) {
                        Object[] options = {"OK ", "CANCEL "};
                        JOptionPane.showOptionDialog(null, "如果你想查询全部数据，请输入一个空格。 ", "提示", JOptionPane.DEFAULT_OPTION,
                                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                        return;
                    }

                    String[] searchArray = str.split(" ");


                    // 删除所有数据
                    System.out.println(newtablemodel.getRowCount());
                    newtablemodel.setDataVector(null, titles);
                    for (int i = 0; i < newtablemodel.getRowCount(); i++) {
                        newtablemodel.removeRow(i);
                    }

                    File file = new File("D:\\aaaaaa");
                    File[] files = file.listFiles();
                    File excelFile = files[0];

                    FileInputStream inputStream = new FileInputStream(excelFile);
                    //new一个workbook
                    HSSFWorkbook workbook = null;
                    workbook = new HSSFWorkbook(inputStream);

                    //创建一个sheet对象，参数为sheet的索引
                    HSSFSheet sheet = workbook.getSheetAt(0);

                    // 表头，添加
                    String[] content;
                    int coloumNum = sheet.getRow(0).getPhysicalNumberOfCells();
                    content = new String[coloumNum + 1];
                    int flag = 0;
                    content[flag] = "序号";
                    for (Cell cell : sheet.getRow(0)) {
                        content[++flag] = cell.getStringCellValue();
                    }

                    newtablemodel.setColumnIdentifiers(content);
                    DecimalFormat df = new DecimalFormat("0");

                    // 表内容，添加
                    for (Row row:sheet) {
                        // 第一行不处理
                        if (row.getRowNum()==0) {
                            continue;
                        }

                        // 从第二行开始处理
                        content = new String[coloumNum + 1];
                        flag = 0;
                        content[flag] = "序号：" + row.getRowNum();

                        // 查询到就显示
                        boolean exits = false;
                        for (Cell cell : row) {
                            if ("STRING".equalsIgnoreCase(cell.getCellType().toString())) {
                                if (Arrays.stream(searchArray).filter(a->cell.getStringCellValue().contains(a)).count()>=searchArray.length) {
                                    exits = true;
                                }
                                content[++flag] = cell.getStringCellValue();
                            }else if("NUMERIC".equalsIgnoreCase(cell.getCellType().toString())){
                                content[++flag] = df.format(cell.getNumericCellValue());
                            }else if("BLANK".equalsIgnoreCase(cell.getCellType().toString())){
                                content[++flag] = "";
                            }else{
                                content[++flag] = "";
                            }
                        }

                        if (exits) {
                            newtablemodel.addRow(content);
                        }
                    }

                    newtablemodel.fireTableDataChanged();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
    }

    public static void main(String[] args) {
        new TestExport();
    }
}
