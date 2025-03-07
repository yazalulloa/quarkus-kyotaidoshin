package com.yaz;

import org.junit.jupiter.api.Test;

public class ArrayAmountTest {

  @Test
  void test() {

    System.out.println(finalArray("45.23".toCharArray()));
  }

  public char[] finalArray(char[] amountArray) {

    char[] finalArray = new char[13];

    final var length = amountArray.length - 1;
    for (int i = 0; i < finalArray.length - length; i++) {
      finalArray[i] = '0';
    }

    boolean hasDot = false;
    for (int i = 0; i <= length; i++) {
      if (amountArray[i] != '.') {
        finalArray[i + finalArray.length - length - (hasDot ? 1 : 0)] = amountArray[i];
      } else {
        hasDot = true;
      }
    }

    return finalArray;
  }

}
