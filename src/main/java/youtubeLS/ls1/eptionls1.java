package youtubeLS.ls1;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class eptionls1 {
    public void readFile (){
        File file = new File("tst.txt");
        Scanner input = null;
        try {
            input = new Scanner(file);
        } catch (FileNotFoundException e) {
            System.out.println("Файл не найден");
        }finally {
            input.close();
        }
    }
    public static void main(String[] args) {


    }




}
