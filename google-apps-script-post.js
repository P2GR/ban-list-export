function doPost(e) {
  var json = JSON.parse(e.postData.contents);
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheetName = 'Sheet1'; // Change this to the name of your sheet
  var sheet = spreadsheet.getSheetByName(sheetName);

  if (!sheet) {
    return ContentService.createTextOutput(`Sheet with name "${sheetName}" not found.`);
  }

  // Define the starting row and column for writing data
  var startRow = 2; // Change this to the desired starting row
  var startColumn = 1; // Change this to the desired starting column

  // Extract the rsn values from the clanBanListMaps key
  var clanBanListMaps = json.clanBanListMaps;
  var rowIndex = startRow;

  if (clanBanListMaps && Array.isArray(clanBanListMaps)) {
    clanBanListMaps.forEach(function(entry) {
      if (entry.rsn) {
        sheet.getRange(rowIndex, startColumn).setValue(entry.rsn);
        rowIndex++;
      }
    });
  }

  return ContentService.createTextOutput(JSON.stringify(json));
}
