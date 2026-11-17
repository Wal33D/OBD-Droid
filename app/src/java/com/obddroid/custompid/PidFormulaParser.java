package com.obddroid.custompid;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import com.obddroid.utils.DebugLog;

/**
 * Parser and evaluator for custom PID formulas
 * Supports expressions like: (A*256+B)/10, A-40, (A*100)/255
 *
 * Variables:
 * - A, B, C, D: Response bytes from OBD
 * - Operations: +, -, *, /, ^, ()
 * - Functions: min, max, abs
 *
 * @author Wal33D
 */
public class PidFormulaParser {
    private static final String TAG = "PidFormulaParser";

    /**
     * Evaluate a formula with given byte values
     *
     * @param formula Formula string, e.g., "(A*256+B)/10"
     * @param bytes   OBD response bytes (A=bytes[0], B=bytes[1], etc.)
     * @return Calculated value
     * @throws FormulaException if formula is invalid or evaluation fails
     */
    public static double evaluate(String formula, int[] bytes) throws FormulaException {
        if (formula == null || formula.trim().isEmpty()) {
            throw new FormulaException("Formula is empty");
        }

        if (bytes == null || bytes.length == 0) {
            throw new FormulaException("No byte values provided");
        }

        try {
            // Substitute variables with byte values
            String expanded = expandVariables(formula, bytes);
            DebugLog.d(TAG, "Formula: " + formula + " -> " + expanded);

            // Evaluate the mathematical expression
            double result = evaluateExpression(expanded);
            DebugLog.d(TAG, "Result: " + result);

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error evaluating formula: " + formula, e);
            throw new FormulaException("Failed to evaluate formula: " + e.getMessage(), e);
        }
    }

    /**
     * Replace A, B, C, D variables with actual byte values
     */
    private static String expandVariables(String formula, int[] bytes) {
        Map<Character, Integer> variables = new HashMap<>();
        if (bytes.length > 0) variables.put('A', bytes[0] & 0xFF);
        if (bytes.length > 1) variables.put('B', bytes[1] & 0xFF);
        if (bytes.length > 2) variables.put('C', bytes[2] & 0xFF);
        if (bytes.length > 3) variables.put('D', bytes[3] & 0xFF);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < formula.length(); i++) {
            char c = formula.charAt(i);
            if (variables.containsKey(c)) {
                result.append(variables.get(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Evaluate a mathematical expression using a simple expression evaluator
     * Supports: +, -, *, /, ^, (), and basic functions
     */
    private static double evaluateExpression(String expression) throws FormulaException {
        expression = expression.replaceAll("\\s+", ""); // Remove whitespace

        // Handle functions first
        expression = handleFunctions(expression);

        // Use Dijkstra's Shunting Yard algorithm + RPN evaluation
        return evaluateRPN(infixToRPN(expression));
    }

    /**
     * Handle functions like min(), max(), abs()
     */
    private static String handleFunctions(String expression) throws FormulaException {
        // Simple function handling - can be expanded
        while (expression.contains("abs(")) {
            int start = expression.indexOf("abs(");
            int end = findMatchingParen(expression, start + 3);
            String inner = expression.substring(start + 4, end);
            double value = Math.abs(evaluateExpression(inner));
            expression = expression.substring(0, start) + value + expression.substring(end + 1);
        }

        while (expression.contains("min(")) {
            int start = expression.indexOf("min(");
            int end = findMatchingParen(expression, start + 3);
            String inner = expression.substring(start + 4, end);
            String[] parts = inner.split(",");
            if (parts.length != 2) throw new FormulaException("min() requires 2 arguments");
            double val1 = evaluateExpression(parts[0]);
            double val2 = evaluateExpression(parts[1]);
            expression = expression.substring(0, start) + Math.min(val1, val2) + expression.substring(end + 1);
        }

        while (expression.contains("max(")) {
            int start = expression.indexOf("max(");
            int end = findMatchingParen(expression, start + 3);
            String inner = expression.substring(start + 4, end);
            String[] parts = inner.split(",");
            if (parts.length != 2) throw new FormulaException("max() requires 2 arguments");
            double val1 = evaluateExpression(parts[0]);
            double val2 = evaluateExpression(parts[1]);
            expression = expression.substring(0, start) + Math.max(val1, val2) + expression.substring(end + 1);
        }

        return expression;
    }

    /**
     * Find matching closing parenthesis
     */
    private static int findMatchingParen(String s, int openPos) throws FormulaException {
        int count = 1;
        for (int i = openPos + 1; i < s.length(); i++) {
            if (s.charAt(i) == '(') count++;
            if (s.charAt(i) == ')') count--;
            if (count == 0) return i;
        }
        throw new FormulaException("Mismatched parentheses");
    }

    /**
     * Convert infix notation to Reverse Polish Notation (RPN)
     */
    private static String infixToRPN(String infix) throws FormulaException {
        StringBuilder output = new StringBuilder();
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < infix.length(); i++) {
            char c = infix.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                // Read full number
                StringBuilder number = new StringBuilder();
                while (i < infix.length() && (Character.isDigit(infix.charAt(i)) || infix.charAt(i) == '.')) {
                    number.append(infix.charAt(i++));
                }
                i--;
                output.append(number).append(' ');
            } else if (c == '(') {
                operators.push(c);
            } else if (c == ')') {
                while (!operators.isEmpty() && operators.peek() != '(') {
                    output.append(operators.pop()).append(' ');
                }
                if (!operators.isEmpty()) {
                    operators.pop(); // Remove '('
                } else {
                    throw new FormulaException("Mismatched parentheses");
                }
            } else if (isOperator(c)) {
                // Handle negative numbers
                if (c == '-' && (i == 0 || infix.charAt(i - 1) == '(' || isOperator(infix.charAt(i - 1)))) {
                    output.append('-');
                    continue;
                }

                while (!operators.isEmpty() && precedence(operators.peek()) >= precedence(c)) {
                    output.append(operators.pop()).append(' ');
                }
                operators.push(c);
            }
        }

        while (!operators.isEmpty()) {
            char op = operators.pop();
            if (op == '(' || op == ')') {
                throw new FormulaException("Mismatched parentheses");
            }
            output.append(op).append(' ');
        }

        return output.toString().trim();
    }

    /**
     * Evaluate Reverse Polish Notation expression
     */
    private static double evaluateRPN(String rpn) throws FormulaException {
        Stack<Double> stack = new Stack<>();
        String[] tokens = rpn.split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            if (isNumeric(token)) {
                stack.push(Double.parseDouble(token));
            } else if (isOperator(token.charAt(0)) && token.length() == 1) {
                if (stack.size() < 2) {
                    throw new FormulaException("Invalid expression");
                }
                double b = stack.pop();
                double a = stack.pop();
                stack.push(applyOperator(token.charAt(0), a, b));
            }
        }

        if (stack.size() != 1) {
            throw new FormulaException("Invalid expression");
        }

        return stack.pop();
    }

    /**
     * Apply binary operator
     */
    private static double applyOperator(char op, double a, double b) throws FormulaException {
        switch (op) {
            case '+':
                return a + b;
            case '-':
                return a - b;
            case '*':
                return a * b;
            case '/':
                if (b == 0) throw new FormulaException("Division by zero");
                return a / b;
            case '^':
                return Math.pow(a, b);
            default:
                throw new FormulaException("Unknown operator: " + op);
        }
    }

    /**
     * Check if character is an operator
     */
    private static boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    /**
     * Get operator precedence
     */
    private static int precedence(char op) {
        switch (op) {
            case '+':
            case '-':
                return 1;
            case '*':
            case '/':
                return 2;
            case '^':
                return 3;
            default:
                return 0;
        }
    }

    /**
     * Check if string is numeric
     */
    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate formula syntax without evaluating
     */
    public static boolean isValidFormula(String formula) {
        try {
            // Try to evaluate with dummy values
            int[] dummyBytes = {0, 0, 0, 0};
            evaluate(formula, dummyBytes);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Invalid formula: " + formula, e);
            return false;
        }
    }

    /**
     * Exception for formula parsing/evaluation errors
     */
    public static class FormulaException extends Exception {
        public FormulaException(String message) {
            super(message);
        }

        public FormulaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
