define(["applications/calendar/tabs/diary/Status",
        "applications/calendar/tabs/Tab",
        "applications/calendar/App"], function(status, Tab, Calendar) {
	
	function render(digest, timeUnit) {
		this.getTemplate("text!applications/calendar/tabs/diary/" + timeUnit.toLowerCase() + "Diary.html", "diary-" + timeUnit, function() {
			setup(digest, timeUnit);
		});
	}
	
	function setup(digest, timeUnit) {
		status.handleComments();
		App.fullHeight();
		$('textarea.tinymce').tinymce({
			// Location of TinyMCE script
			script_url : '/static/tiny_mce/tiny_mce.js',

			// General options
			theme : "advanced",
			plugins : "style",
			width : "100%",
			height: "95%",

			// Theme options
			theme_advanced_buttons1 : "bold,italic,underline,|,justifyleft,justifycenter,justifyright,justifyfull",
			theme_advanced_buttons2 : "",
			theme_advanced_buttons3 : "",
			theme_advanced_buttons4 : "",
			theme_advanced_toolbar_location : "top",
			theme_advanced_toolbar_align : "left",
			theme_advanced_resizing : false,

			// Example content CSS (should be your site CSS)
			content_css : "/static/tiny_mce/css/content.css",
		});
	}
	
	var diaryTab = new Tab("diary", "Candide Kemmler", "icon-pencil", true);
	diaryTab.render = render;
	return diaryTab;
	
});