package Util;

import com.eclipsesource.v8.V8;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BypassCloudFlare {

    final String OPERATION_PATTERN = "setTimeout\\(function\\(\\)\\{\\s+(var s,t,o,p,b,r,e,a,k,i,n,g,f.+?\\r?\\n[\\s\\S]+?a\\.value =.+?)\\r?\\n";
    final String PASS_PATTERN = "name=\"pass\" value=\"(.+?)\"";
    final String CHALLENGE_PATTERN = "name=\"jschl_vc\" value=\"(\\w+)\"";
    private CookieManager cm;
    private String UA;
    private String url;

    public BypassCloudFlare(String url) {
        this.url = url;
    }

    public List<HttpCookie> cookies() {
        cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cm);

        try {
            URL ConnUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) ConnUrl.openConnection();
            conn.setRequestProperty("User-Agent", getUA());
            conn.connect();
            if (conn.getResponseCode() == 503) {
                InputStream is = conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String str;
                while ((str = br.readLine()) != null) {
                    sb.append(str + System.lineSeparator());
                }
                is.close();
                br.close();

                CookieStore ck = cm.getCookieStore();

                conn.disconnect();

                str = sb.toString();
                String jschl_vc = regex(str, "name=\"jschl_vc\" value=\"(.+?)\"").get(0);    //正则取值
                String pass = regex(str, "name=\"pass\" value=\"(.+?)\"").get(0);            //
                double jschl_answer = get_answer(str);                                         //计算结果
                Thread.sleep(4000);

                String req = String.valueOf("https://" + ConnUrl.getHost()) + "/cdn-cgi/l/chk_jschl?"
                        + "jschl_vc=" + jschl_vc + "&pass=" + pass + "&jschl_answer=" + jschl_answer;
                HttpURLConnection.setFollowRedirects(false);
                System.out.println(req);
                HttpURLConnection reqconn = (HttpURLConnection) new URL(req).openConnection();

                reqconn.setRequestProperty("Referer", req);
                reqconn.setRequestProperty("User-Agent", getUA());
                reqconn.setRequestProperty("Cookie", ck.getCookies().toString());
                reqconn.connect();
                if (reqconn.getResponseCode() == 302) {
                    CookieStore ck1 = cm.getCookieStore();
                    reqconn.disconnect();

                    /*
                      同上
                     */
                    HttpURLConnection conn302 = (HttpURLConnection) new URL(req).openConnection();
                    conn302.setRequestProperty("Referer", ConnUrl.getHost());
                    conn302.setRequestProperty("User-Agent", getUA());
                    conn302.setRequestProperty("Cookie", ck1.getCookies().toString());
                    conn302.connect();
                    if (conn302.getResponseCode() == 302) {
                        CookieStore ck2 = cm.getCookieStore();
                        conn302.disconnect();
                        return ck2.getCookies();
                    }
                }
            } else {
                CookieStore cookieStore = cm.getCookieStore();
                return cookieStore.getCookies();

            }
        } catch (NullPointerException e) {
        } catch (IndexOutOfBoundsException e) {
            CookieStore cookieStore = cm.getCookieStore();
            return cookieStore.getCookies();
        } catch (IOException | InterruptedException e) {
        }
        return null;
    }

    private double get_answer(String content) {  //取值
        double a = 0;
        try {
            String js = get_matches(content, OPERATION_PATTERN).get(0);
            js = js.replaceAll("a\\.value = (.+ \\+ t\\.length).+", "$1");
            js = js.replaceAll("\\s{3,}[a-z](?: = |\\.).+", "");
            js = js.replace("t.length", "11");
            js = js.replace("[\\n\\\\']", "");

            V8 v8 = V8.createV8Runtime();
            a = v8.executeDoubleScript(js);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return a;
    }

    public static double round(double value, int places) {
        DecimalFormat decim = new DecimalFormat("0.0000000000");
        return Double.parseDouble(decim.format(value));
    }

    private String getUA() {
        return UA;
    }

    public void setUA(String UA) {
        this.UA = UA;
    }

    private List<String> regex(String text, String pattern) {    //正则
        try {
            Pattern pt = Pattern.compile(pattern);
            Matcher mt = pt.matcher(text);
            List<String> group = new ArrayList<>();

            while (mt.find()) {
                if (mt.groupCount() >= 1) {
                    if (mt.groupCount() > 1) {
                        group.add(mt.group(1));
                        group.add(mt.group(2));
                    } else {
                        group.add(mt.group(1));
                    }

                }
            }
            return group;
        } catch (NullPointerException e) {
        }
        return null;
    }

    public Map<String, String> List2Map(List<HttpCookie> list) {  //转换为jsoup可用的map
        Map<String, String> map = new HashMap<>();
        try {
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    String[] listStr = list.get(i).toString().split("=");
                    map.put(listStr[0], listStr[1]);
                }
            } else {
                return map;
            }

        } catch (IndexOutOfBoundsException e) {
        }

        return map;
    }

    public static List<String> get_matches(String s, String p) {
        try {
            Files.write(Paths.get("a.txt"), s.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException ex) {
            Logger.getLogger(BypassCloudFlare.class.getName()).log(Level.SEVERE, null, ex);
        }
        // returns all matches of p in s for first group in regular expression 
        List<String> matches = new ArrayList<String>();
        Matcher m = Pattern.compile(p).matcher(s);
        while (m.find()) {
            matches.add(m.group(1));
        }
        return matches;
    }

    public static void main(String[] args) {
        BypassCloudFlare cf = new BypassCloudFlare("https://bittrex.com");
        cf.setUA("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.66 Safari/537.36");
        List<HttpCookie> httpCookieList = cf.cookies();
        for (HttpCookie httpCookie : httpCookieList) {
            System.out.println(httpCookie.getName());
        }
    }
}
