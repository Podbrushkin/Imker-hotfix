package wiki;

public class WikiCategory implements Comparable<WikiCategory> {

	private String name;
	private String sortkey;
	private WikiCategory[] children;
	public static final String CATEGORY_PREFIX = "Category:";

	/**
	 * Construct a WikiCategory which may have children
	 * 
	 * @param name
	 *            the name of the category which may or may not contain the
	 *            namespace prefix
	 * @param sortkey
	 *            Either a String with the sortkey or null
	 * @param children
	 *            the children of the category or null
	 */
	public WikiCategory(String name, String sortkey, WikiCategory[] children) {
		this.name = CATEGORY_PREFIX
				+ WikiPage.firstCharToUpperCase(name.replaceFirst("(?i)^"
						+ CATEGORY_PREFIX, ""));
		this.sortkey = sortkey;
		this.children = children == null ? new WikiCategory[] {} : children;

	}

	/**
	 * Return the name of the category with the namespace prefix
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Return the sortkey of the category
	 * 
	 * @return the sortkey
	 */
	public String getSortkey() {
		return sortkey;
	}

	/**
	 * Return the children of this category
	 * 
	 * @return the array of children
	 */
	public WikiCategory[] getChildren() {
		return children;
	}

	/**
	 * Compares two WikiCategory by comparing their names
	 * 
	 * @param otherCat
	 *            the WikiCategory to compare
	 * @return the result of the String comparison
	 */
	@Override
	public int compareTo(WikiCategory otherCat) {
		return this.getName().compareTo(otherCat.getName());
	}
}
