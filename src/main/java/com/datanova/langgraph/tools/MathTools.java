package com.datanova.langgraph.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * MathTools provides mathematical operations that can be used by workflow nodes.
 *
 * <p>This utility class contains various mathematical functions including basic arithmetic
 * operations, statistical calculations, and unit conversions. Each method is annotated
 * with @Description to make it discoverable by LLMs when used as function tools.</p>
 *
 * <p>Available operations:</p>
 * <ul>
 *   <li><b>Basic arithmetic</b> - add, subtract, multiply, divide</li>
 *   <li><b>Statistics</b> - sum, average</li>
 *   <li><b>Unit conversion</b> - Celsius to Fahrenheit temperature conversion</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Component
public class MathTools {

    /**
     * Adds two numbers together and returns the sum.
     *
     * @param a the first number
     * @param b the second number
     * @return the sum of a and b
     */
    @Description("Adds two numbers together and returns the sum")
    public double add(double a, double b) {
        double result = a + b;
        log.debug("add({}, {}) = {}", a, b, result);
        return result;
    }

    /**
     * Subtracts the second number from the first and returns the difference.
     *
     * @param a the number to subtract from
     * @param b the number to subtract
     * @return the difference (a - b)
     */
    @Description("Subtracts the second number from the first and returns the difference")
    public double subtract(double a, double b) {
        double result = a - b;
        log.debug("subtract({}, {}) = {}", a, b, result);
        return result;
    }

    /**
     * Multiplies two numbers together and returns the product.
     *
     * @param a the first number
     * @param b the second number
     * @return the product of a and b
     */
    @Description("Multiplies two numbers together and returns the product")
    public double multiply(double a, double b) {
        double result = a * b;
        log.debug("multiply({}, {}) = {}", a, b, result);
        return result;
    }

    /**
     * Divides the first number by the second and returns the quotient.
     *
     * @param a the dividend
     * @param b the divisor
     * @return the quotient (a / b)
     * @throws ArithmeticException if b is zero
     */
    @Description("Divides the first number by the second and returns the quotient. Throws exception if divisor is zero")
    public double divide(double a, double b) {
        if (b == 0) {
            log.error("Division by zero attempted: divide({}, {})", a, b);
            throw new ArithmeticException("Cannot divide by zero");
        }
        double result = a / b;
        log.debug("divide({}, {}) = {}", a, b, result);
        return result;
    }

    /**
     * Calculates the average (arithmetic mean) of a list of numbers.
     *
     * <p>The average is computed by summing all numbers and dividing by the count.</p>
     *
     * @param numbers the list of numbers to average
     * @return the arithmetic mean of the numbers
     * @throws IllegalArgumentException if the numbers list is null or empty
     */
    @Description("Calculates the average (mean) of a list of numbers")
    public double calculateAverage(List<Double> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            log.error("calculateAverage called with null or empty list");
            throw new IllegalArgumentException("Numbers list cannot be empty");
        }
        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / numbers.size();
        log.debug("calculateAverage({} numbers) = {}", numbers.size(), average);
        return average;
    }

    /**
     * Converts temperature from Celsius to Fahrenheit.
     *
     * <p>Uses the conversion formula: F = C × (9/5) + 32</p>
     *
     * @param celsius the temperature in Celsius
     * @return the temperature in Fahrenheit
     */
    @Description("Converts temperature from Celsius to Fahrenheit using the formula F = C * (9/5) + 32")
    public double celsiusToFahrenheit(double celsius) {
        double fahrenheit = (celsius * 9.0 / 5.0) + 32.0;
        log.debug("celsiusToFahrenheit({}°C) = {}°F", celsius, fahrenheit);
        return fahrenheit;
    }

    /**
     * Calculates the sum of all numbers in a list.
     *
     * @param numbers the list of numbers to sum
     * @return the total sum of all numbers
     * @throws IllegalArgumentException if the numbers list is null or empty
     */
    @Description("Calculates the sum of all numbers in a list")
    public double calculateSum(List<Double> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            log.error("calculateSum called with null or empty list");
            throw new IllegalArgumentException("Numbers list cannot be empty");
        }
        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        log.debug("calculateSum({} numbers) = {}", numbers.size(), sum);
        return sum;
    }
}
