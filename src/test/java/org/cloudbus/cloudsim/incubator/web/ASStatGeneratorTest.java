package org.cloudbus.cloudsim.incubator.web;

import static org.cloudbus.cloudsim.incubator.util.helpers.TestUtil.createSeededGaussian;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.incubator.web.extensions.WebCloudlet;
import org.junit.Before;
import org.junit.Test;
import org.uncommons.maths.number.NumberGenerator;

/**
 * 
 * @author nikolay.grozev
 * 
 */
public class ASStatGeneratorTest {

    private static final int GEN_RAM_MEAN = 200;
    private static final int GEN_RAM_STDEV = 10;
    private NumberGenerator<Double> genRAM;

    private static final int GEN_CPU_MEAN = 25;
    private static final int GEN_CPU_STDEV = 2;
    private NumberGenerator<Double> genCPU;

    private static IWebBroker dummyBroker;

    private Map<String, NumberGenerator<Double>> testGenerators = new HashMap<>();

    @Before
    public void setUP() {
	dummyBroker = mock(IWebBroker.class);
	when(dummyBroker.getVmList()).thenReturn(new ArrayList<Vm>());
	when(dummyBroker.getId()).thenReturn(1);
	
	genRAM = createSeededGaussian(GEN_RAM_MEAN, GEN_RAM_STDEV);
	genCPU = createSeededGaussian(GEN_CPU_MEAN, GEN_CPU_STDEV);
	testGenerators = new HashMap<>();
	testGenerators.put(ASStatGenerator.CLOUDLET_LENGTH, genCPU);
	testGenerators.put(ASStatGenerator.CLOUDLET_RAM, genRAM);
    }

    @Test
    public void testHandlingEmptyAndNonemtpyCases() {
	// Should be empty in the start
	ASStatGenerator generator = new ASStatGenerator(dummyBroker, testGenerators);
	assertTrue(generator.isEmpty());
	assertNull(generator.peek());
	assertNull(generator.poll());

	generator.notifyOfTime(15);

	// Should not be empty now
	assertFalse(generator.isEmpty());
	Object peeked = generator.peek();
	Object peekedAgain = generator.peek();
	Object polled = generator.poll();
	assertNotNull(peeked);
	assertTrue(peeked == peekedAgain);
	assertTrue(peeked == polled);

	// Should be empty again
	assertTrue(generator.isEmpty());
	assertNull(generator.peek());
	assertNull(generator.poll());
    }

    @Test
    public void testHandlingTimeConstraints() {
	// Should be empty in the start
	ASStatGenerator generator = new ASStatGenerator(dummyBroker, testGenerators, 3, 12);

	// Notify for times we are not interested in...
	generator.notifyOfTime(2);
	generator.notifyOfTime(15);
	generator.notifyOfTime(17);

	// Should be empty now...
	assertTrue(generator.isEmpty());
	assertNull(generator.peek());
	assertNull(generator.poll());

	// Notify for times again.
	generator.notifyOfTime(2); // Not Interested
	generator.notifyOfTime(5); // Interested
	generator.notifyOfTime(5); // Not Interested - it is repeated
	generator.notifyOfTime(7); // Interested
	generator.notifyOfTime(10); // Interested
	generator.notifyOfTime(10); // Not Interested - it is repeated
	generator.notifyOfTime(18); // Not Interested

	// Should not be empty now
	assertFalse(generator.isEmpty());
	Object peeked = generator.peek();
	Object peekedAgain = generator.peek();
	assertTrue(peeked == peekedAgain);

	// Check if we have 3 things in the generator
	int i = 0;
	while (!generator.isEmpty()) {
	    peeked = generator.peek();
	    peekedAgain = generator.peek();
	    Object polled = generator.poll();
	    assertNotNull(peeked);
	    assertTrue(peeked == peekedAgain);
	    assertTrue(peeked == polled);
	    i++;
	}
	assertEquals(3, i);

	// Should be empty again... we polled everything
	assertTrue(generator.isEmpty());
	assertNull(generator.peek());
	assertNull(generator.poll());
    }

    @Test
    public void testStatisticsAreUsedOK() {
	ASStatGenerator generator = new ASStatGenerator(dummyBroker, testGenerators);

	DescriptiveStatistics ramStat = new DescriptiveStatistics();
	DescriptiveStatistics cpuStat = new DescriptiveStatistics();

	// Generate 100 values
	int size = 100;
	for (int i = 0; i < size; i++) {
	    generator.notifyOfTime(i + 5);
	}

	// Compute descriptive statistics
	for (int i = 0; i < size; i++) {
	    WebCloudlet c = generator.poll();
	    ramStat.addValue(c.getRam());
	    cpuStat.addValue(c.getCloudletLength());
	}

	//Allow for delta, because of using doubles, and rounding some of the numbers
	double delta = 10;
	assertEquals(GEN_RAM_MEAN, ramStat.getMean(), delta);
	assertEquals(GEN_RAM_STDEV, ramStat.getStandardDeviation(), delta);
	assertEquals(GEN_CPU_MEAN, cpuStat.getMean(), delta);
	assertEquals(GEN_CPU_STDEV, cpuStat.getStandardDeviation(), delta);

	// Assert we have exhausted the generator
	assertTrue(generator.isEmpty());
    }
}
