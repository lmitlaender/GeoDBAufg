

import java.util.ArrayList;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.Point;

import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;

import fu.keys.LSIClassCentreDB;
import fu.util.DBUtil;

import java.io.File;
import java.io.IOException;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;



public class ImageDemo {

    private static GeometryFactory geomfact=new GeometryFactory();


    public static void main(String args[]) {
        Connection connection=null;  


        long time;
        int cnt;
        ResultSet resultSet;
        Statement statement;


        if (args.length!=1) {
            System.out.println("DB Access String as single param expected");
            System.exit(1);
        }


        try {
            /* Zugang zur Datenbank einrichten */
            DBUtil.parseDBparams(args[0],0);
            connection=DBUtil.getConnection(0);
            connection.setAutoCommit(false);  //Getting results based on a cursor
            LSIClassCentreDB.initFromDB(connection);
        }
        catch (Exception e) {
            System.out.println("Error initialising DB access: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }

        try {
            // Leeres Bild anlegen
            BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) image.getGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten


// Eine Objektgeomtrie wird aus der Datenbank geholt und fuer dorenda gemalt

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("select realname,ST_AsEWKB(geom :: geometry) from domain where realname ='Lorenzer Platz' AND geometry='A'");

            cnt=0;
 
            while (resultSet.next()) {
                int col=1;
                String realname=resultSet.getString(col++);
                byte[] geomdata=resultSet.getBytes(col++);
                Geometry geom=new WKBReader().read(geomdata);
                System.out.println(realname);

                paintGeometry(g,geom,realname,"fontain");

                cnt++;
            }
            resultSet.close();

            System.out.println("Schreibe Bild ...");
            g.dispose();
            ImageIO.write(image, "PNG", new File("demoimage.png"));  // Als die Karte als PNG speichern
            System.out.println("Bild geschrieben");

        }
        catch (Exception e) {
            System.out.println("Error processing DB queries: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }   
    }


    public static void paintGeometry(Graphics2D g,Geometry geom,String name,String icon) throws IOException {
        if (geom instanceof Polygon) {

            LineString extring=((Polygon)geom).getExteriorRing();
            int n=extring.getNumPoints();

            double minX= 1e10;
            double minY= 1e10;
            double maxX=-1e10;
            double maxY=-1e10;

            for (int i=0;i<n;i++) {
                Coordinate coord=extring.getCoordinateN(i);
                minX=Math.min(minX,coord.x);
                minY=Math.min(minY,coord.y);
                maxX=Math.max(maxX,coord.x);
                maxY=Math.max(maxY,coord.y);
            }


            int[] x=new int[n];
            int[] y=new int[n];

            for (int i=0;i<n;i++) {
                Coordinate coord=extring.getCoordinateN(i);
                x[i]=(int)Math.round((coord.x-minX)*512/(maxX-minX));
                y[i]=511-(int)Math.round((coord.y-minY)*512/(maxY-minY));
            }

            g.setColor(Color.RED);
            g.drawPolygon(x,y,x.length);

            BufferedImage iconImage=ImageIO.read(new File("icons"+File.separator+icon+".png"));
            g.drawImage(iconImage,200,220,null);

            g.drawString(name,200,200);

        }
        else
            throw new IllegalArgumentException("Don't know how to paint "+geom.getClass());
    }





}