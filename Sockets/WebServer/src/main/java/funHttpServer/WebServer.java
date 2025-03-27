/*
Simple Web Server in Java which allows you to call 
localhost:9000/ and show you the root.html webpage from the www/root.html folder
You can also do some other simple GET requests:
1) /random shows you a random picture (well random from the set defined)
2) json shows you the response as JSON for /random instead the html page
3) /file/filename shows you the raw file (not as HTML)
4) /multiply?num1=3&num2=4 multiplies the two inputs and responses with the result
5) /github?query=users/amehlhase316/repos (or other GitHub repo owners) will lead to receiving
   JSON which will for now only be printed in the console. See the todo below

The reading of the request is done "manually", meaning no library that helps making things a 
little easier is used. This is done so you see exactly how to pars the request and 
write a response back
*/

package funHttpServer;

import javax.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.charset.Charset;

class WebServer {
  public static void main(String args[]) {
    WebServer server = new WebServer(9000);
  }

  /**
   * Main thread
   * @param port to listen on
   */
  public WebServer(int port) {
    ServerSocket server = null;
    Socket sock = null;
    InputStream in = null;
    OutputStream out = null;

    try {
      server = new ServerSocket(port);
      while (true) {
        sock = server.accept();
        out = sock.getOutputStream();
        in = sock.getInputStream();
        byte[] response = createResponse(in);
        out.write(response);
        out.flush();
        in.close();
        out.close();
        sock.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (sock != null) {
        try {
          server.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Used in the "/random" endpoint
   */
  private final static HashMap<String, String> _images = new HashMap<>() {
    {
      put("streets", "https://iili.io/JV1pSV.jpg");
      put("bread", "https://iili.io/Jj9MWG.jpg");
    }
  };

  private Random random = new Random();

  /**
   * Reads in socket stream and generates a response
   * @param inStream HTTP input stream from socket
   * @return the byte encoded HTTP response
   */
  public byte[] createResponse(InputStream inStream) {

    byte[] response = null;
    BufferedReader in = null;

    try {

      // Read from socket's input stream. Must use an
      // InputStreamReader to bridge from streams to a reader
      in = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));

      // Get header and save the request from the GET line:
      // example GET format: GET /index.html HTTP/1.1

      String request = null;

      boolean done = false;
      while (!done) {
        String line = in.readLine();

        System.out.println("Received: " + line);

        // find end of header("\n\n")
        if (line == null || line.isEmpty())
          done = true;
        // parse GET format ("GET <path> HTTP/1.1")
        else if (line.startsWith("GET")) {
          int firstSpace = line.indexOf(" ");
          int secondSpace = line.indexOf(" ", firstSpace + 1);

          // extract the request, basically everything after the GET up to HTTP/1.1
          request = line.substring(firstSpace + 2, secondSpace);
        }

      }
      System.out.println("FINISHED PARSING HEADER\n");

      // Generate an appropriate response to the user
      if (request == null) {
        response = "<html>Illegal request: no GET</html>".getBytes();
      } else {
        // create output buffer
        StringBuilder builder = new StringBuilder();
        // NOTE: output from buffer is at the end

        if (request.isEmpty()) {
          defaultPage(builder);
        } else if (request.equalsIgnoreCase("json")) {
          json(builder);
        } else if (request.equalsIgnoreCase("random")) {
          random(builder);
        } else if (request.contains("file/")) {
          file(request, builder);
        } else if (request.contains("multiply?")) {
          multiply(request, builder);
        } else if (request.contains("github?")) {
          github(request, builder);
        } else if (request.contains("weather?")){
          weather(request, builder);
        } else if (request.contains("base-convert?")) {
          baseConvert(request, builder);
        } else {
          // if the request is not recognized at all
          builder.append(buildResponse(
                  "400 Bad Request",
                  "I am not sure what you want me to do..."));
        }
        // Output
        response = builder.toString().getBytes();
      }
    } catch (IOException e) {
      e.printStackTrace();
      response = ("<html>ERROR: " + e.getMessage() + "</html>").getBytes();
    }

    return response;
  }

  /**
   * Parse a query to receive weather forecast data from NOAA's API based on a
   * given latitude and longitude. This query supports both cardinal direction notation and
   * direct decimal notation.
   * <p>Examples of appropriate requests:</p>
   * <ul>
   *     <li><code>/weather?lat=39.7456&lon=-97.0892</code> (Linn, KS)</li>
   *     <li><code>/weather?lat=47.6061N&lon=122.3328W</code> (Seattle, WA)</li>
   * </ul>
   * <p>Using the free NOAA API</p>
   * @param request request string containing query information
   * @param builder response builder for server reply
   *
   * @see <a href="https://www.weather.gov/documentation/services-web-api">NOAA API Documentation</a>
   */
  private void weather(String request, StringBuilder builder) {
    // given a lat/long parse into a weather service APi to get current forecast for that area
    // this will require two API requests from NOAA: one for finding the grid endpoint, and
    // another for querying the grid endpoint for the forecast. This is all returned as well-defined
    // JSON so it should be really easy to parse.
    // https://www.weather.gov/documentation/services-web-api

    // System.out.println("I've made it to weather!");

    try {
      int latSign = 1, lonSign = 1;
      Map<String,String> query_pairs = splitQuery(request.replace("weather?",""));

      if (query_pairs.size() < 2)
        throw new Exception("400.1");
      if (!query_pairs.containsKey("lat") || !query_pairs.containsKey("lon") ||
              query_pairs.get("lat").isEmpty() || query_pairs.get("lon").isEmpty())
        throw new Exception("400.2");


      // check lat/lon sign
      if (query_pairs.get("lat").contains("S")
              || query_pairs.get("lat").contains("s")
              || query_pairs.get("lat").contains("-"))
        latSign = -1;
      if (query_pairs.get("lon").contains("W")
              || query_pairs.get("lon").contains("w")
              || query_pairs.get("lon").contains("-"))
        lonSign = -1;

      // clean the input (remove all but numeric values)
      String cleanLat = query_pairs.get("lat").replaceAll("[^\\d.]", "");
      String cleanLon = query_pairs.get("lon").replaceAll("[^\\d.]", "");
      // get double values
      double lat = Double.parseDouble(cleanLat) * latSign;
      double lon = Double.parseDouble(cleanLon) * lonSign;
      // check within range
      if (lat < -90 || lat > 90)
        throw new NumberFormatException("422.1");
      if (lon < -180 || lon > 180)
        throw new NumberFormatException("422.2");

      //System.out.println("lat = " + lat + " lon = " + lon);

      // begin API calls
      String firstJson = fetchURL("https://api.weather.gov/points/" + lat + "," + lon);
      if (firstJson == null || firstJson.trim().isEmpty()) {
        throw new Exception("404.1"); // no data returned from NOAA
      }

      // System.out.println(firstJson);

      StringBuilder htmlBuilder = new StringBuilder();
      htmlBuilder.append("<html><body><h1>Forecast</h1>");

      try (JsonReader firstJsonReader = Json.createReader(new StringReader(firstJson))) {
        JsonObject properties = firstJsonReader.readObject().getJsonObject("properties");
        JsonObject relativeLocation = properties.getJsonObject("relativeLocation");
        String city = relativeLocation.getJsonObject("properties").getString("city");
        String state = relativeLocation.getJsonObject("properties").getString("state");
        double relLat = relativeLocation.getJsonObject("geometry")
                .getJsonArray("coordinates").getJsonNumber(1).doubleValue();
        double relLon = relativeLocation.getJsonObject("geometry")
                .getJsonArray("coordinates").getJsonNumber(0).doubleValue();
        String forecastUrl = properties.getJsonString("forecast").getString();

        htmlBuilder.append("<strong>").append(city).append(", ").append(state).append("</strong><br>");
        htmlBuilder.append("At relative coordinates: ").append(relLat).append(" ").append(relLon).append("<br>");

        // System.out.println("Forecast URL: " + forecastUrl);

        String secondJson = fetchURL(forecastUrl);

        // System.out.println(secondJson);

        if (secondJson == null || secondJson.trim().isEmpty()) {
          throw new Exception("404.2"); // no data returned from NOAA
        }
        JsonReader secondJsonReader = Json.createReader(new StringReader(secondJson));
        JsonArray forecastArray = secondJsonReader.readObject()
                .getJsonObject("properties").getJsonArray("periods");
        // organized as 0 = today, 1 = tonight? We'll display both.
        String firstName = forecastArray.get(0).asJsonObject().getString("name");
        String firstForecast = forecastArray.get(0).asJsonObject().getString("detailedForecast");
        String secondName = forecastArray.get(1).asJsonObject().getString("name");
        String secondForecast = forecastArray.get(1).asJsonObject().getString("detailedForecast");

        htmlBuilder.append("<ul><li>").append("<strong>").append(firstName).append("</strong>:<br>").append(firstForecast).append("<br></li>");
        htmlBuilder.append("<li><strong>").append(secondName).append("</strong>:<br>").append(secondForecast).append("<br></li></ul>");

      } catch (JsonException e) {
        throw new JsonException("422.3"); // json parsing error
      } catch (Exception e) {
        throw new Exception(e);
      }

      htmlBuilder.append("</body></html>");
      builder.append(buildResponse(
              "200 OK",
              htmlBuilder.toString()));

    } catch (UnsupportedEncodingException e) {
      builder.append(buildResponse(
              "415",
              "Error: Input not in UTF-8 format."));
    } catch (NumberFormatException e) {
      if (e.getMessage().equals("422.1")) {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: Latitude must be between -90 and 90 degrees, or between 0-90N/S."));
      } else if (e.getMessage().equals("422.2")) {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: Longitude must be between -180 and 180 degrees, or between 0-180E/W."));
      } else {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: can not parse input into a number."));
      }
    } catch (JsonException e) {
      if (e.getMessage().equals("422.3")) {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: could not parse NOAA response"));
      }
    } catch (Exception e) {
      if (e.getMessage().equals("400.1")) {
        builder.append(buildResponse(
                "400 Bad Request",
                "Error: less than two arguments supplied."));
      } else if (e.getMessage().equals("400.2")) {
        builder.append(buildResponse(
                "400 Bad Request",
                "Error: missing fields, or fields are empty."));
      } else if (e.getMessage().equals("404.1")) {
        builder.append(buildResponse(
                "404 Not Found",
                "Error: No data returned from weather.gov API (1)."));
      } else if (e.getMessage().equals("404.2")) {
        builder.append(buildResponse(
                "404 Not Found",
                "Error: No data returned from weather.gov API (2)."));
      } else {
        System.out.println(e.getMessage());
        builder.append(buildResponse(
                "500 Internal Server Error",
                "Unknown error occurred"));
      }
    }
  }

  /**
   * Parse a query to convert a number from base x to base y.
   * <p>Parameters:
   * <li><code>num</code>: number to convert</li>
   * <li><code>from</code>: base of the number</li>
   * <li><code>to</code>: desired base</li>
   * </p>
   * <p>Example query:</p>
   * <p><code>/base-convert?num=23&from=10&to=2</code></p>
   * @param request request string containing query information
   * @param builder response builder for server reply
   */
  private void baseConvert(String request, StringBuilder builder) {
    try {
      int radixFrom, radixTo, number;

      Map<String, String> query_pairs;
      query_pairs = splitQuery(request.replace("base-convert?", ""));

      if (query_pairs.size() < 3)
        throw new Exception("400.1");
      if (!query_pairs.containsKey("num") || query_pairs.get("num") == null
              || !query_pairs.containsKey("from") || query_pairs.get("from") == null
              || !query_pairs.containsKey("to") || query_pairs.get("to") == null)
        throw new Exception("400.2");

      // check radixes
      radixFrom = Integer.parseInt(query_pairs.get("from"));
      radixTo = Integer.parseInt(query_pairs.get("to"));
      if (radixFrom < Character.MIN_RADIX || radixFrom > Character.MAX_RADIX
              || radixTo < Character.MIN_RADIX || radixTo > Character.MAX_RADIX)
        throw new NumberFormatException("422.1");

      try {
        number = Integer.parseInt(query_pairs.get("num"), radixFrom);
      } catch (NumberFormatException e) {
        throw new NumberFormatException("422.2");
      }

      String result = Integer.toString(number, radixTo).toUpperCase();

      builder.append(buildResponse(
              "200 OK",
              "Result: " + query_pairs.get("num")
                      + " from base " + radixFrom
                      + " to " + radixTo
                      + " = " + result));

    } catch (UnsupportedEncodingException e) {
      builder.append(buildResponse(
              "415",
              "Error: Input not in UTF-8 format."));
    } catch (NumberFormatException e) {
      // 400.3 invalid base format
      if (e.getMessage().equals("422.1")) {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: Invalid base format, supported bases are 2-36."
        ));
      // 400.4 invalid number for conversion from format
      } else if (e.getMessage().equals("422.2")) {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: Invalid number for base from"
        ));
      } else {
        builder.append(buildResponse(
                "422 Unprocessable Content",
                "Error: can not parse input into a number."));
      }
    } catch (Exception e) {
      // 400.1 not enough arguments
      if (e.getMessage().equals("400.1")) {
        builder.append(buildResponse(
                "400 Bad Request",
                "Error: less than three arguments supplied."));
      // 400.2 incorrect or empty arguments
      } else if (e.getMessage().equals("400.2")) {
        builder.append(buildResponse(
                "400 Bad Request",
                "Error: missing fields, or fields are empty."));
      } else {
        builder.append(buildResponse(
                "500 Internal Server Error",
                "Unknown error occurred"));
      }
    }
  }

  private static void defaultPage(StringBuilder builder) throws IOException {
    // shows the default directory page

    // opens the root.html file
    String page = new String(readFileInBytes(new File("www/root.html")));
    // performs a template replacement in the page
    page = page.replace("${links}", buildFileList());

    // Generate response
    builder.append(buildResponse(
            "200 OK",
            page));
  }

  private void json(StringBuilder builder) {
    // shows the JSON of a random image and sets the header name for that image

    // pick an index from the map
    int index = random.nextInt(_images.size());

    // pull out the information
    String header = (String) _images.keySet().toArray()[index];
    String url = _images.get(header);
    StringBuilder json = new StringBuilder();

    // Generate response
    json.append("{");
    json.append("\"header\":\"").append(header).append("\",");
    json.append("\"image\":\"").append(url).append("\"");
    json.append("}");
    builder.append(buildResponse(
            "200 OK",
            json.toString()));
  }

  private void github(String request, StringBuilder builder) {
    try {
      // pulls the query from the request and runs it with GitHub's REST API
      // check out https://docs.github.com/rest/reference/
      //
      // HINT: REST is organized by nesting topics. Figure out the biggest one first,
      //     then drill down to what you care about
      // "Owner's repo is named RepoName. Example: find RepoName's contributors" translates to
      //     "/repos/OWNERNAME/REPONAME/contributors"

      Map<String, String> query_pairs = splitQuery(request.replace("github?", ""));
      if(!query_pairs.containsKey("query"))
        throw new Exception("400.1"); // missing query parameter

      String jsonResponse = fetchURL("https://api.github.com/" + query_pairs.get("query"));
      // System.out.println(json);

      if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
        throw new Exception("404.1"); // no data returned from GitHub
      }

      StringBuilder htmlBuilder = new StringBuilder();
      htmlBuilder.append("<html><body><h1>GitHub Repositories</h1><ul>");

      try (JsonReader jsonReader = Json.createReader(new StringReader(jsonResponse))) {
        JsonArray repos = jsonReader.readArray();
        if (repos.isEmpty()) {
          htmlBuilder.append("<p>No repositories found</p>");
        } else {
          for (JsonValue value : repos) {
            JsonObject repo = (JsonObject) value;
            String fullName = repo.getString("full_name");
            int id = repo.getInt("id");
            String ownerLogin = repo.getJsonObject("owner").getString("login");

            htmlBuilder.append("<li>")
                    .append("<strong>Full Name:</strong> ").append(fullName).append("<br>")
                    .append("<strong>ID:</strong> ").append(id).append("<br>")
                    .append("<strong>Owner:</strong> ").append(ownerLogin)
                    .append("</li>");
          }
        }
      } catch (JsonException e) {
        throw new Exception("422.1"); // json parsing error
      }

      htmlBuilder.append("</ul></body></html>");

      builder.append(buildResponse(
              "200 OK",
              htmlBuilder.toString()));

    } catch (UnsupportedEncodingException e) {
      builder.append(buildResponse(
              "415 Unsupported Media Type",
              "Error: Invalid URL encoding"));
    } catch (Exception e) {
        if (e.getMessage() != null) {
          switch (e.getMessage()) {
            case "400.1":
              builder.append(buildResponse(
                      "400 Bad Request",
                      "Error: Missing 'query' parameter"));
              break;
            case "404.1":
              builder.append(buildResponse(
                      "404 Not Found",
                      "Error: No data returned from GitHub API"));
              break;
            case "422.1":
              builder.append(buildResponse(
                      "422 Unprocessable Content",
                      "Error: Could not parse GitHub response"));
              break;
            default:
              builder.append(buildResponse(
                      "HTTP/1.1 500 Internal Server Error\n",
                      "Error: " + e.getMessage()));
          }
        } else {
          builder.append(buildResponse(
                  "500 Internal Server Error",
                  "Unknown error occurred"));
        }
    }
  }

  private static void multiply(String request, StringBuilder builder) {
    try {
      Map<String, String> query_pairs;
      // extract path parameters
      query_pairs = splitQuery(request.replace("multiply?", ""));

      if (query_pairs.size() < 2)
        throw new Exception("400.1");
      if (!query_pairs.containsKey("num1") || !query_pairs.containsKey("num2") ||
              query_pairs.get("num1").isEmpty() || query_pairs.get("num2").isEmpty())
        throw new Exception("400.2");

      // extract required fields from parameters
      Integer num1 = Integer.parseInt(query_pairs.get("num1"));
      Integer num2 = Integer.parseInt(query_pairs.get("num2"));

      // do math
      Integer result = num1 * num2;

      // Generate response
      builder.append(buildResponse(
              "200 OK",
              "Result is: " + result));

    } catch (NumberFormatException e) {
      builder.append(buildResponse(
              "422 Unprocessable Content",
              "Error: can not parse input into a number."));
    } catch (UnsupportedEncodingException e) {
      builder.append(buildResponse(
              "415 Unsupported Media Type",
              "Error: Input not in UTF-8 format."));
    } catch (Exception e) {
      if (e.getMessage().equals("400.1")) {
        builder.append(buildResponse(
                "400 Bad Request",
                "Error: less than two arguments supplied."));
      } else if (e.getMessage().equals("400.2")) {
        builder.append(buildResponse(
                "400 Bad Request",
                "Error: fields num1 or num2 not in entry."));
      } else {
        builder.append(buildResponse(
                "500 Internal Server Error",
                "Unknown error occurred"));
      }
    }
  }

  private static void file(String request, StringBuilder builder) {
    // tries to find the specified file and shows it or shows an error

    // take the path and clean it. try to open the file
    File file = new File(request.replace("file/", ""));

    // Generate response
    if (file.exists()) { // success
      builder.append(buildResponse(
              "200 OK",
              "Would theoretically be a file but removed this part, you do not have to do anything with it for the assignment"));
    } else { // failure
      builder.append(buildResponse(
              "404 Not Found",
              "File not found: " + file));
    }
  }

  private static void random(StringBuilder builder) throws IOException {
    // opens the random image page

    // open the index.html
    File file = new File("www/index.html");

    // Generate response
    builder.append(buildResponse(
            "200 OK",
            new String(readFileInBytes(file))));
  }

  /**
   * Method to read in a query and split it up correctly
   * @param query parameters on path
   * @return Map of all parameters and their specific values
   * @throws UnsupportedEncodingException If the URLs aren't encoded with UTF-8
   */
  public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
    Map<String, String> query_pairs = new LinkedHashMap<String, String>();
    // "q=hello+world%2Fme&bob=5"
    String[] pairs = query.split("&");
    // ["q=hello+world%2Fme", "bob=5"]
    for (String pair : pairs) {
      int idx = pair.indexOf("=");
      query_pairs.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
    }
    // {{"q", "hello world/me"}, {"bob","5"}}
    return query_pairs;
  }

  /**
   * Builds an HTML file list from the www directory
   * @return HTML string output of file list
   */
  public static String buildFileList() {
    ArrayList<String> filenames = new ArrayList<>();

    // Creating a File object for directory
    File directoryPath = new File("www/");
    filenames.addAll(Arrays.asList(directoryPath.list()));

    if (!filenames.isEmpty()) {
      StringBuilder builder = new StringBuilder();
      builder.append("<ul>\n");
      for (var filename : filenames) {
        builder.append("<li>").append(filename).append("</li>");
      }
      builder.append("</ul>\n");
      return builder.toString();
    } else {
      return "No files in directory";
    }
  }

  /**
   * Read bytes from a file and return them in the byte array. We read in blocks
   * of 512 bytes for efficiency.
   */
  public static byte[] readFileInBytes(File f) throws IOException {

    FileInputStream file = new FileInputStream(f);
    ByteArrayOutputStream data = new ByteArrayOutputStream(file.available());

    byte buffer[] = new byte[512];
    int numRead = file.read(buffer);
    while (numRead > 0) {
      data.write(buffer, 0, numRead);
      numRead = file.read(buffer);
    }
    file.close();

    byte[] result = data.toByteArray();
    data.close();

    return result;
  }

  /**
   *
   * a method to make a web request. Note that this method will block execution
   * for up to 20 seconds while the request is being satisfied. Better to use a
   * non-blocking request.
   * 
   * @param aUrl the String indicating the query url for the OMDb api search
   * @return the String result of the http request.
   *
   **/
  public String fetchURL(String aUrl) {
    StringBuilder sb = new StringBuilder();
    URLConnection conn = null;
    InputStreamReader in = null;
    try {
      URL url = new URL(aUrl);
      conn = url.openConnection();
      if (conn != null)
        conn.setReadTimeout(20 * 1000); // timeout in 20 seconds
      if (conn != null && conn.getInputStream() != null) {
        in = new InputStreamReader(conn.getInputStream(), Charset.defaultCharset());
        BufferedReader br = new BufferedReader(in);
        if (br != null) {
          int ch;
          // read the next character until end of reader
          while ((ch = br.read()) != -1) {
            sb.append((char) ch);
          }
          br.close();
        }
      }
      in.close();
    } catch (Exception ex) {
      System.out.println("Exception in url request:" + ex.getMessage());
    }
    return sb.toString();
  }

  private static String buildResponse(String httpCode, String htmlMessage) {
    return "HTTP/1.1 "
            + httpCode
            + "\n"
            + "Content-Type: text/html; charset=utf-8\n"
            + "\n"
            + htmlMessage;
  }
}
