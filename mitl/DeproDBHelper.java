package mitl;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTWriter;
import fu.keys.LSIClassCentreDB;
import fu.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class QueryData {
    public Connection connection;
    public PreparedStatement preparedStatement;
    public ResultSet resultSet;

    public QueryData(Connection connection, PreparedStatement preparedStatement, ResultSet resultSet) {
        this.connection = connection;
        this.preparedStatement = preparedStatement;
        this.resultSet = resultSet;
    }

    public void close() {
        try {
            if (resultSet != null && !resultSet.isClosed()) {
                resultSet.close();
            }
            if (preparedStatement != null && !preparedStatement.isClosed()) {
                preparedStatement.close();
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("Error closing resources: " + e.toString());
            e.printStackTrace();
        }
    }
}

public class DeproDBHelper {
    static String dbAccessString = "127.0.0.1/5432/dbuser/dbuser/deproDBMittelfrankenPG";
    static boolean mainConnectionInitialised = false;

    public static Connection initMainConnection() {
        Connection connection = null;

        try {
            DBUtil.parseDBparams(dbAccessString,0);
            connection=DBUtil.getConnection(0);
            connection.setAutoCommit(false);  //Getting results based on a cursor
            LSIClassCentreDB.initFromDB(connection);
            mainConnectionInitialised = true;
        }
        catch (Exception e) {
            System.out.println("Error initialising DB access: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }

        return connection;
    }

    public static Connection getMainConnection() {
        if (!mainConnectionInitialised) {
            return initMainConnection();
        } else {
            try {
                Connection connection = DBUtil.getConnection(0);
                if (connection == null || connection.isClosed()) {
                    return initMainConnection();
                } else {
                    return connection;
                }
            } catch (Exception e) {
                System.out.println("Error getting main connection: " + e.toString());
                e.printStackTrace();
                return null;
            }
        }
    }

    public static ResultSet getMainResultSet(Geometry queryGeometry) {
        Connection connection = getMainConnection();
        ResultSet resultSet = null;

        try {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT d_id, realname, lsiclass1, lsiclass2, lsiclass3, tags, tags_name, ST_AsEWKB(geom :: geometry)
                    FROM domain WHERE ST_Intersects(geom :: geometry, ST_GeomFromText(?,4326))
                    ORDER BY d_id ASC
                    """
            );

            int col=1;
            preparedStatement.setString(col++, new WKTWriter().write(queryGeometry));

            System.out.println("Querying with "+preparedStatement.toString());

            preparedStatement.setFetchSize(1000);

            return preparedStatement.executeQuery();
        } catch (SQLException e) {
            System.out.println("Error executing query: " + e.toString());
            e.printStackTrace();
            return null;
        }
    }

    public static String getImportId(int d_id) {

        try {
            Connection connection = getMainConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT importid
                    FROM domain WHERE d_id=?
                    """
            );

            int col=1;
            preparedStatement.setInt(col++, d_id);
            preparedStatement.setFetchSize(1000);
            ResultSet resultSet = preparedStatement.executeQuery();

            String importId = null;
            if (resultSet.next()) {
                importId = resultSet.getString(1);
            }
            resultSet.close();
            preparedStatement.close();
            connection.close();

            return importId;
        } catch (SQLException e) {
            System.out.println("Error executing query: " + e.toString());
            e.printStackTrace();
            return null;
        }
    }

    public static QueryData getRelationComponents(String role, String importId) {
        DBUtil.parseDBparams(dbAccessString, 1);

        try {Connection connection = DBUtil.getConnection(1);
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT realname, role, ST_AsEWKB(domain.geom :: geometry)
                    FROM relation
                    JOIN domain ON relation.member_d_id = domain.d_id
                    WHERE relation.importid = ? AND relation.role = ?
                    """
            );

            int col=1;
            preparedStatement.setString(col++, importId);
            preparedStatement.setString(col++, role);

            preparedStatement.setFetchSize(1000);

            return new QueryData(connection, preparedStatement, preparedStatement.executeQuery());
        } catch (Exception e) {
            System.out.println("Error executing query: " + e.toString());
            e.printStackTrace();
            return null;
        }
    }
}
