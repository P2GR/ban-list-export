function doGet(e) {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheetName = 'Sheet1'; // Change this to the name of your sheet
  var sheet = spreadsheet.getSheetByName(sheetName);

  if (!sheet) {
    return ContentService.createTextOutput(`Sheet with name "${sheetName}" not found.`);
  }

  // Define the starting row and manually select columns
  var startRow = 2; // Change this to the desired starting row
  var usernameColumn = 1; // Change this to the column number for usernames
  var dateColumn = 2; // Change this to the column number for date
  var bannedByColumn = 3; // Change this to the column number for banned_by
  var reasonColumn = 4; // Change this to the column number for reason

  var numRows = sheet.getLastRow() - startRow + 1; // Number of rows to read

  // Read the data from the specified range
  var usernames = sheet.getRange(startRow, usernameColumn, numRows, 1).getValues().flat();
  var dates = sheet.getRange(startRow, dateColumn, numRows, 1).getValues().flat();
  var bannedBy = sheet.getRange(startRow, bannedByColumn, numRows, 1).getValues().flat();
  var reasons = sheet.getRange(startRow, reasonColumn, numRows, 1).getValues().flat();


  // Create an object for each user with their corresponding data
  var usersData = {};
  usernames.forEach(function(username, index) {
    if (username) { // Ensure username is not empty
      usersData[username] = {
        date: dates[index] || '',
        bannedBy: bannedBy[index] || '',
        reason: reasons[index] || ''
      };
    }
  });

  // Return the JSON response
  var jsonResponse = JSON.stringify({ users: usersData });
  return ContentService.createTextOutput(jsonResponse).setMimeType(ContentService.MimeType.JSON);
}