package com.strobel.expressions;

/**
 * Represents the values of run-time variables.
 * @author Mike Strobel
 */
public interface IRuntimeVariables
{
    int size();
    Object get(final int index);
    Object set(final int index, final Object value);
}