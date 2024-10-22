
# Clan Ban List Enhanced
This plugin is able to export your clan's ban list to a CSV or JSON file.
It can also send the ban list to a web server or google sheet via a POST request.
The plugin can also import bans from a web server or google sheet via a GET request.

By importing bans using a GET request, you are able to have an extended ban list that is not limited to the 100 bans that the game allows. This is useful for clans that have a large ban list and need to keep track of all the bans.

### Features
* Export banned usernames in CSV or JSON.
* Export to clipboard.
* Send POST request to a web server with the bans.
* Import bans using a GET request from a web server.


## How to: export to clipboard
1. Once logged into the game open the Clan interface via Clan Chat channel 'Settings' button.
2. Click 'Bans' on the left side.
3. Export should now be in your clipboard ready to paste.



## How to: export to Google Sheets
1. Create a new Google Sheet.
2. Go to `Extensions` -> `Apps Script`.
3. Copy the code from the `google-apps-script-post.js` file (or below) and paste it into the script editor.
4. Save the script.
5. Go to `Publish` -> `Deploy as web app`.
6. Set the `Project version` to `New` and `Execute as` to `Me`.
7. Set `Who has access to the app` to `Anyone, even anonymous`.
8. Click `Deploy`.
9. Copy the `Current web app URL` and paste it into the `Export URL` field in the plugin configuration.
```javascript
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
```




## How to: import bans from Google Sheets

1. Create a new Google Sheet.
2. Go to `Extensions` -> `Apps Script`.
3. Copy the code below and paste it into the script editor. You can paste it next to the export script from the previous guide.
4. Save the script.
5. Go to `Publish` -> `Deploy as web app`.
6. Set the `Project version` to `New` and `Execute as` to `Me`.
7. Set `Who has access to the app` to `Anyone, even anonymous`.
8. Click `Deploy`.
9. Copy the `Current web app URL` and paste it into the `Import URL` field in the plugin configuration.

```javascript
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
```


## How to: import bans from a web server

1. Create a new endpoint on your web server that returns a JSON array of usernames.
2. Paste the URL of the endpoint into the `Import URL` field in the plugin configuration.
3. The endpoint should return a JSON array of usernames like this:
```json
{
  "usernames": [
    "username1",
    "username2",
    "username3"
  ]
}
```

## How to: send bans to a web server

1. Create a new endpoint on your web server that accepts a POST request with a JSON array of usernames.
2. Paste the URL of the endpoint into the `Export URL` field in the plugin configuration.
3. The plugin will send a POST request to the specified URL with the following JSON body:
```json
{
  "clanBanListMaps" : [
    {
      "rsn": "username1"
    },
    {
      "rsn": "username2"
    },
    {
      "rsn": "username3"
    }
  ]
}
```
