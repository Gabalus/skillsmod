package net.puffish.skillsmod.arpg.metric;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public interface ConditionExpression {
	boolean test(ResolveContext context);

	String describe();

	static ConditionExpression always() {
		return Always.INSTANCE;
	}

	static ConditionExpression flag(String flag) {
		return new Flag(flag);
	}

	static ConditionExpression not(ConditionExpression term) {
		return new Not(term);
	}

	static ConditionExpression all(ConditionExpression... terms) {
		return new All(Arrays.asList(terms));
	}

	static ConditionExpression any(ConditionExpression... terms) {
		return new Any(Arrays.asList(terms));
	}

	enum Always implements ConditionExpression {
		INSTANCE;

		@Override
		public boolean test(ResolveContext context) {
			return true;
		}

		@Override
		public String describe() {
			return "always";
		}
	}

	record Flag(String flag) implements ConditionExpression {
		public Flag {
			if (flag == null) {
				throw new IllegalArgumentException("flag cannot be null");
			}
			flag = flag.trim().toLowerCase(Locale.ROOT);
			if (flag.isEmpty()) {
				throw new IllegalArgumentException("flag cannot be blank");
			}
		}

		@Override
		public boolean test(ResolveContext context) {
			return context.hasFlag(flag);
		}

		@Override
		public String describe() {
			return flag;
		}
	}

	record Not(ConditionExpression term) implements ConditionExpression {
		public Not {
			if (term == null) {
				throw new IllegalArgumentException("term cannot be null");
			}
		}

		@Override
		public boolean test(ResolveContext context) {
			return !term.test(context);
		}

		@Override
		public String describe() {
			return "not(" + term.describe() + ")";
		}
	}

	record All(List<ConditionExpression> terms) implements ConditionExpression {
		public All {
			if (terms == null || terms.stream().anyMatch(java.util.Objects::isNull)) {
				throw new IllegalArgumentException("terms cannot contain null");
			}
			terms = List.copyOf(terms);
		}

		@Override
		public boolean test(ResolveContext context) {
			return terms.stream().allMatch(term -> term.test(context));
		}

		@Override
		public String describe() {
			return terms.stream().map(ConditionExpression::describe).collect(java.util.stream.Collectors.joining(" and ", "(", ")"));
		}
	}

	record Any(List<ConditionExpression> terms) implements ConditionExpression {
		public Any {
			if (terms == null || terms.stream().anyMatch(java.util.Objects::isNull)) {
				throw new IllegalArgumentException("terms cannot contain null");
			}
			terms = List.copyOf(terms);
		}

		@Override
		public boolean test(ResolveContext context) {
			return terms.stream().anyMatch(term -> term.test(context));
		}

		@Override
		public String describe() {
			return terms.stream().map(ConditionExpression::describe).collect(java.util.stream.Collectors.joining(" or ", "(", ")"));
		}
	}
}
