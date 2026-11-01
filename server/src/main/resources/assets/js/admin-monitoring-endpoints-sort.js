(function () {
	var table = document.getElementById("endpointsTable");
	if (!table) return;
	var tbody = table.querySelector("tbody");
	var headers = Array.from(table.querySelectorAll("th[data-col]"));
	var sortCol = -1, sortAsc = true;

	headers.forEach(function (th) {
		th.addEventListener("click", function () {
			var col = parseInt(th.getAttribute("data-col"));
			var isNum = th.getAttribute("data-type") === "num";
			if (sortCol === col) {
				sortAsc = !sortAsc;
			} else {
				sortCol = col;
				sortAsc = !isNum; // numeric → desc first; string → asc first
			}
			headers.forEach(function (h) { h.classList.remove("mon-sort-asc", "mon-sort-desc"); });
			th.classList.add(sortAsc ? "mon-sort-asc" : "mon-sort-desc");

			var rows = Array.from(tbody.querySelectorAll("tr"));
			rows.sort(function (a, b) {
				var aVal = (a.querySelectorAll("td")[col] || {}).getAttribute("data-val") || "";
				var bVal = (b.querySelectorAll("td")[col] || {}).getAttribute("data-val") || "";
				var cmp = isNum ? parseFloat(aVal) - parseFloat(bVal) : aVal.localeCompare(bVal);
				return sortAsc ? cmp : -cmp;
			});
			rows.forEach(function (r) { tbody.appendChild(r); });
		});
	});
})();
