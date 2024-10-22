function doGet(e) {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheetName = 'Sheet1'; // Change this to the name of your sheet
  var sheet = spreadsheet.getSheetByName(sheetName);

  if (!sheet) {
    return ContentService.createTextOutput(`Sheet with name "${sheetName}" not found.`);
  }

  // Define the starting row and column for reading data
  var startRow = 2; // Change this to the desired starting row
  var startColumn = 1; // Change this to the desired starting column
  var numRows = sheet.getLastRow() - startRow + 1; // Number of rows to read

  // Read the usernames from the specified range
  var usernames = sheet.getRange(startRow, startColumn, numRows, 1).getValues().flat().filter(String);

  // Return the usernames as a JSON array
  var jsonResponse = JSON.stringify({ usernames: usernames });
  return ContentService.createTextOutput(jsonResponse).setMimeType(ContentService.MimeType.JSON);
}