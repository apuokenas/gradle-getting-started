import ratpack.exec.Blocking;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.groovy.template.TextTemplateModule;
import ratpack.guice.Guice;

import static ratpack.groovy.Groovy.groovyTemplate;

import java.util.*;
import java.sql.*;

import com.heroku.sdk.jdbc.DatabaseUrl;

// Add jscience
import static javax.measure.unit.SI.KILOGRAM;
import javax.measure.quantity.Mass;
import org.jscience.physics.model.RelativisticModel;
import org.jscience.physics.amount.Amount;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(s -> s
        .serverConfig(c -> c
          .baseDir(BaseDir.find())
          .env())

        .registry(Guice.registry(b -> b
          .module(TextTemplateModule.class, conf -> conf.setStaticallyCompile(true))))

        .handlers(chain -> chain
            .get(ctx -> ctx.render(groovyTemplate("index.html")))

            // Add jscience
            /*.get("hello", ctx -> {
              RelativisticModel.select();

              Amount<Mass> m = Amount.valueOf("12 GeV").to(KILOGRAM);
              ctx.render("E=mc^2: 12 GeV = " + m.toString());
            })*/
            .get("hello", ctx -> {
              RelativisticModel.select();

              // Grab an energy value from the ENERGY environment variable
              String energy = System.getenv("ENERGY");

              Amount<Mass> m = Amount.valueOf(energy).to(KILOGRAM);
              ctx.render("E=mc^2: " + energy + " = " + m.toString());
            })

            // When somebody accesses the app using the /db route, a new row will be added to the tick table,
            // and all the rows will then be returned so that they can be rendered in the output
            .get("db", ctx -> {
              boolean local = !"cedar-14".equals(System.getenv("STACK"));

              Blocking.get(() -> {
                Connection connection = null;

                try {
                  connection = DatabaseUrl.extract(local).getConnection();
                  Statement stmt = connection.createStatement();
                  stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)");
                  stmt.executeUpdate("INSERT INTO ticks VALUES (now())");
                  return stmt.executeQuery("SELECT tick FROM ticks");
                } finally {
                  if (connection != null) try {
                    connection.close();
                  } catch (SQLException e) {
                  }
                }
              }).onError(throwable -> {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("message", "There was an error: " + throwable);
                ctx.render(groovyTemplate(attributes, "error.html"));
              }).then(rs -> {
                ArrayList<String> output = new ArrayList<>();
                while (rs.next()) {
                  output.add("Read from DB: " + rs.getTimestamp("tick"));
                }

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("results", output);
                ctx.render(groovyTemplate(attributes, "db.html"));
              });
            })

            .files(f -> f.dir("public"))
        )
    );
  }
}