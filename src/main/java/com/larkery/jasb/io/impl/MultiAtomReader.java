package com.larkery.jasb.io.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.larkery.jasb.bind.Bind;
import com.larkery.jasb.io.IAtomReader;
import com.larkery.jasb.io.IReadContext;
import com.larkery.jasb.sexp.Atom;
import com.larkery.jasb.sexp.errors.UnexpectedTermError;

class MultiAtomReader<T> {
	private final Class<T> clazz;
	private final ImmutableSet<IAtomReader> delegates;
	private final Set<String> legalValues;
	private final ImmutableMap<String, Class<?>> fallbacks;

	public MultiAtomReader(final Class<T> clazz, final ImmutableSet<IAtomReader> build, final ImmutableSet<Class<?>> fallbacks) {
		this.clazz = clazz;
		this.delegates = build;
		
		final ImmutableMap.Builder<String, Class<?>> fallbackBuilder = ImmutableMap.builder();
		
		final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
		for (final IAtomReader delegate : delegates) {
			builder.addAll(delegate.getLegalValues(clazz));
		}
		legalValues = builder.build();
		
		for (final Class<?> c : fallbacks) {
			if (c.isAnnotationPresent(Bind.class)) {
				fallbackBuilder.put(c.getAnnotation(Bind.class).value(), c);
			}
		}
		
		this.fallbacks = fallbackBuilder.build();
	}

	protected ListenableFuture<T> read(final IReadContext context, final Atom atom) {
		for (final IAtomReader reader : delegates) {
			final Optional<T> value = reader.read(atom.getValue(), clazz);
			if (value.isPresent()) {
				return Futures.immediateFuture(value.get());
			}
		}
		
		if (fallbacks.containsKey(atom.getValue())) {
			try {
				return Futures.immediateFuture(clazz.cast(fallbacks.get(atom.getValue()).getConstructor().newInstance()));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			}
		}
		
		context.handle(
				new UnexpectedTermError(atom, legalValues, atom.getValue()));
		
		return Futures.immediateFailedFuture(new RuntimeException("Could not read " + atom + " as " + clazz));
	}

}
