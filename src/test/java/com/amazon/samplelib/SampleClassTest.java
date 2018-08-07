package com.amazon.samplelib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 *  SampleClassTest.
 */
public class SampleClassTest {
    @Test
    public void sampleMethodTest() {
        SampleClass sampleClass = new SampleClass();
        assertEquals(sampleClass.sampleMethod(), "sampleMethod() called!");
    }
}
