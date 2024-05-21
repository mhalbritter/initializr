/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.initializr.generator.buildsystem.gradle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A customization for a Gradle extension.
 *
 * @author Moritz Halbritter
 */
public class GradleExtension {

	private final String name;

	private final List<Attribute> attributes;

	private final List<Invocation> invocations;

	private final Map<String, GradleExtension> nested;

	private final Set<String> importedTypes;

	protected GradleExtension(Builder builder) {
		this.name = builder.name;
		this.attributes = List.copyOf(builder.attributes);
		this.invocations = List.copyOf(builder.invocations);
		this.nested = Collections.unmodifiableMap(resolve(builder.nested));
		this.importedTypes = Collections.unmodifiableSet(builder.getImportedTypes());
	}

	private static Map<String, GradleExtension> resolve(Map<String, Builder> extensions) {
		Map<String, GradleExtension> result = new LinkedHashMap<>();
		extensions.forEach((name, builder) -> result.put(name, builder.build()));
		return result;
	}

	/**
	 * Return the name of the extension.
	 * @return the extension name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Return the attributes that should be configured for this extension.
	 * @return extension attributes
	 */
	public List<Attribute> getAttributes() {
		return this.attributes;
	}

	/**
	 * Return the {@link Invocation invocations} of this extension.
	 * @return extension invocations
	 */
	public List<Invocation> getInvocations() {
		return this.invocations;
	}

	/**
	 * Return nested {@link GradleExtension extensions}.
	 * @return nested extensions
	 */
	public Map<String, GradleExtension> getNested() {
		return this.nested;
	}

	/**
	 * Return the imported types.
	 * @return imported types
	 */
	public Set<String> getImportedTypes() {
		return this.importedTypes;
	}

	/**
	 * A builder for {@link GradleExtension}.
	 */
	public static class Builder {

		private final String name;

		private final List<Attribute> attributes = new ArrayList<>();

		private final List<Invocation> invocations = new ArrayList<>();

		private final Map<String, Builder> nested = new LinkedHashMap<>();

		private final Set<String> importedTypes = new HashSet<>();

		protected Builder(String name) {
			this.name = name;
		}

		/**
		 * Set a extension attribute.
		 * @param target the name of the attribute
		 * @param value the value
		 */
		public void attribute(String target, String value) {
			this.attributes.add(Attribute.set(target, value));
		}

		/**
		 * Set an extension attribute with a type.
		 * @param target the name of the attribute
		 * @param value the value
		 * @param type the type to import
		 */
		public void attributeWithType(String target, String value, String type) {
			this.importedTypes.add(type);
			attribute(target, value);
		}

		/**
		 * Configure an extension attribute by appending the specified value.
		 * @param target the name of the attribute
		 * @param value the value to append
		 */
		public void append(String target, String value) {
			this.attributes.add(Attribute.append(target, value));
		}

		/**
		 * Configure an extension attribute by appending the specified value and type.
		 * @param target the name of the attribute
		 * @param value the value to append
		 * @param type the type to import
		 */
		public void appendWithType(String target, String value, String type) {
			this.importedTypes.add(type);
			append(target, value);
		}

		/**
		 * Invoke an extension method.
		 * @param target the name of the method
		 * @param arguments the arguments
		 */
		public void invoke(String target, String... arguments) {
			this.invocations.add(new Invocation(target, Arrays.asList(arguments)));
		}

		/**
		 * Customize a nested extension for the specified name. If such nested extension
		 * has already been added, the consumer can be used to further tune the existing
		 * extension configuration.
		 * @param name a extension name
		 * @param customizer a {@link Consumer} to customize the nested extension
		 */
		public void nested(String name, Consumer<Builder> customizer) {
			customizer.accept(this.nested.computeIfAbsent(name, (ignored) -> new Builder(name)));
		}

		/**
		 * Build a {@link GradleExtension} with the current state of this builder.
		 * @return a {@link GradleExtension}
		 */
		public GradleExtension build() {
			return new GradleExtension(this);
		}

		/**
		 * Returns the imported types of this extension and all nested ones.
		 * @return the imported types
		 */
		Set<String> getImportedTypes() {
			Set<String> result = new HashSet<>(this.importedTypes);
			for (Builder nested : this.nested.values()) {
				result.addAll(nested.getImportedTypes());
			}
			return result;
		}

	}

	/**
	 * An invocation of a method that customizes an extension.
	 */
	public static class Invocation {

		private final String target;

		private final List<String> arguments;

		Invocation(String target, List<String> arguments) {
			this.target = target;
			this.arguments = arguments;
		}

		/**
		 * Return the name of the method.
		 * @return the method name
		 */
		public String getTarget() {
			return this.target;
		}

		/**
		 * Return the arguments (can be empty).
		 * @return the method arguments
		 */
		public List<String> getArguments() {
			return this.arguments;
		}

	}

	/**
	 * An attribute of an extension.
	 */
	public static final class Attribute {

		private final String name;

		private final String value;

		private final Attribute.Type type;

		private Attribute(String name, String value, Attribute.Type type) {
			this.name = name;
			this.value = value;
			this.type = type;
		}

		/**
		 * Create an attribute that {@linkplain Attribute.Type#SET sets} the specified
		 * value.
		 * @param name the name of the attribute
		 * @param value the value to set
		 * @return an attribute
		 */
		public static Attribute set(String name, String value) {
			return new Attribute(name, value, Attribute.Type.SET);
		}

		/**
		 * Create an attribute that {@linkplain Attribute.Type#APPEND appends} the
		 * specified value.
		 * @param name the name of the attribute
		 * @param value the value to append
		 * @return an attribute
		 */
		public static Attribute append(String name, String value) {
			return new Attribute(name, value, Attribute.Type.APPEND);
		}

		/**
		 * Return the name of the attribute.
		 * @return the name
		 */
		public String getName() {
			return this.name;
		}

		/**
		 * Return the value of the attribute to set or to append.
		 * @return the value
		 */
		public String getValue() {
			return this.value;
		}

		/**
		 * Return the {@link Attribute.Type} of the attribute.
		 * @return the type
		 */
		public Attribute.Type getType() {
			return this.type;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Attribute attribute = (Attribute) o;
			return Objects.equals(this.name, attribute.name) && Objects.equals(this.value, attribute.value)
					&& this.type == attribute.type;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.name, this.value, this.type);
		}

		@Override
		public String toString() {
			return this.name + ((this.type == Attribute.Type.SET) ? " = " : " += ") + this.value;
		}

		public enum Type {

			/**
			 * Set the value of the attribute.
			 */
			SET,

			/**
			 * Append the value to the attribute.
			 */
			APPEND;

		}

	}

}
