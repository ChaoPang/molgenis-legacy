package org.molgenis.framework.ui.html;

public class AutocompleteInput<E> extends HtmlInput<E>
{
	private String entityClass;
	private String entityField;

	public AutocompleteInput(String name, String label, String entityClass, String entityField, String description)
	{
		super(name, label, null, true, false, description);
		this.entityClass = entityClass;
		this.entityField = entityField;
	}
	
	@Override
	public String toHtml()
	{
		return
				"<input id=\"" + this.getId() + "\" name=\"" + getName() + "\" type=\"text\" onfocus=\"autoComplete(this)\"/>" +
				"<script type=\"text/javascript\">\n" +
				"function autoComplete(elem) {\n" +
				"	$(elem).autocomplete({\n" +
				"		source: function(req, resp) {\n" +
				"			var url         = \"xref/find\";\n" +
				"			var suggestions = [];\n" +
				"			successFunction = function(data, textStatus) {\n" +
				"				$.each(data, function(key, val) { suggestions.push(key); });\n" +
				"				return suggestions;\n" +
				"			};\n" +
				"			var dataHash = new Object();\n" +
				"			dataHash['xref_entity'] = '" + this.entityClass + "';\n" +
				"			dataHash['xref_field']  = '" + this.entityField + "';\n" +
				"			dataHash['xref_label']  = '" + this.entityField + "';\n" +
				"			dataHash['xref_label_search'] = req.term;\n" +
				"			jQuery.ajax({ url: url, data: dataHash, dataType: \"json\", type: \"POST\", async: false, success: successFunction });\n" +
				"			resp(suggestions);\n" +
				"		},\n" +
				"		select: function(e, ui) { },\n" +
				"		change: function() { }\n" +
				"	});\n" +
				"}\n" +
				"</script>\n";
	}
}
