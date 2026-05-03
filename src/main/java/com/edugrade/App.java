package com.edugrade;

import com.edugrade.servlet.MainServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class App {
    public static void main(String[] args) throws Exception {
        int port = 8080;

        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Register our servlet for all paths
        context.addServlet(new ServletHolder(new MainServlet()), "/*");

        server.setHandler(context);
        server.start();

        System.out.println("==================================================");
        System.out.println("  EduGrade Pro is running!");
        System.out.println("  Open browser: http://localhost:" + port);
        System.out.println("  Press Ctrl+C to stop");
        System.out.println("==================================================");

        server.join();
    }
}
