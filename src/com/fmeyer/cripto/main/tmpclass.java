package com.fmeyer.cripto.main;

/**
 * Created by IntelliJ IDEA.
 * User: Fernando
 * Date: Aug 15, 2006
 * Time: 3:00:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class tmpclass extends B {
    tmpclass( int ... arrr ){
        //super(arrr);
    }

    public static void main ( String args[] ){
        tmpclass a = new tmpclass( 1,2,3,4,5);
        a.print();
    }
}

class B {
     int array[];

     B(int...barr) {
         array = barr;
         System.out.print("aaaaaaaa");
     }

     void print() {
         for (int i : array)
         System.out.println(i);
     }
 }
