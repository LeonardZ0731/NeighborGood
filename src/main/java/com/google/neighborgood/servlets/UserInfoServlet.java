// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.neighborgood.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.GeoPt;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import com.google.neighborgood.User;
import com.google.neighborgood.helper.UnitConversion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/account")
public class UserInfoServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    UserService userService = UserServiceFactory.getUserService();

    // Retrieves user accounts for the topscorers board
    if (request.getParameterMap().containsKey("action")
        && request.getParameter("action").equals("topscorers")) {
      List<User> users = new ArrayList<User>();
      try {
        users = retrieveTopTenUsers(request, userService, datastore);
      } catch (IllegalArgumentException e) {
        response.setContentType("text/html");
        response.getWriter().println("Your location coordinates are invalid");
        return;
      }
      Gson gson = new Gson();
      response.setContentType("application/json;");
      response.getWriter().println(gson.toJson(users));
      return;
    }

    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(userService.createLoginURL("/account.jsp"));
      return;
    }

    Query query =
        new Query("UserInfo")
            .setFilter(
                new FilterPredicate(
                    "userId", FilterOperator.EQUAL, userService.getCurrentUser().getUserId()));
    PreparedQuery results = datastore.prepare(query);
    Entity entity = results.asSingleEntity();

    if (entity == null) {
      System.err.println("Unable to find the UserInfo entity based on the current user id");
      response.sendError(
          HttpServletResponse.SC_NOT_FOUND, "The requested user info could not be found");
      return;
    }
    List<String> result = new ArrayList<>();
    result.add((String) entity.getProperty("nickname"));
    result.add((String) entity.getProperty("address"));
    result.add((String) entity.getProperty("phone"));
    result.add((String) entity.getProperty("zipcode"));
    result.add((String) entity.getProperty("country"));

    Gson gson = new Gson();
    String json = gson.toJson(result);
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    UserService userService = UserServiceFactory.getUserService();
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(userService.createLoginURL("/account.jsp"));
      return;
    }

    String nickname = "";
    String address = "";
    String phone = "";
    String zipcode = "";
    String country = "";
    Float lat = null;
    Float lng = null;
    String nicknameInput = request.getParameter("nickname-input");
    String addressInput = request.getParameter("address-input");
    String phoneInput = request.getParameter("phone-input");
    String zipcodeInput = request.getParameter("zipcode-input");
    String countryInput = request.getParameter("country-input");
    String email = userService.getCurrentUser().getEmail();
    String userId = userService.getCurrentUser().getUserId();

    try {
      lat = Float.parseFloat(request.getParameter("lat-input"));
      lng = Float.parseFloat(request.getParameter("lng-input"));
    } catch (NumberFormatException e) {
      System.err.println("Invalid location coordinates");
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid location coordinates");
    }
    GeoPt location = new GeoPt(lat, lng);

    if (nicknameInput != null) nickname = nicknameInput.trim();
    if (addressInput != null) address = addressInput.trim();
    if (phoneInput != null) phone = phoneInput.trim();
    if (zipcodeInput != null) zipcode = zipcodeInput.trim();
    if (countryInput != null) country = countryInput.trim();

    if (nickname.equals("")
        || address.equals("")
        || phone.equals("")
        || country.equals("")
        || zipcode.equals("")) {
      System.err.println("At least one input field is empty");
      return;
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    Query query =
        new Query("UserInfo")
            .setFilter(new FilterPredicate("userId", FilterOperator.EQUAL, userId));
    PreparedQuery results = datastore.prepare(query);
    Entity entity = results.asSingleEntity();
    if (entity == null) {
      entity = new Entity("UserInfo", userId);
      entity.setProperty("nickname", nickname);
      entity.setProperty("address", address);
      entity.setProperty("zipcode", "59715");
      entity.setProperty("country", "United States");
      entity.setProperty("location", location);
      entity.setProperty("phone", phone);
      entity.setProperty("email", email);
      // "userId" now becomes obsolete as the entity
      // ID/Name is the same value as userId.
      // I will modify this in all servlets
      // in a different PR
      entity.setProperty("userId", userId);
      entity.setProperty("country", country);
      entity.setProperty("zipcode", zipcode);
      entity.setProperty("points", 0);
    } else {
      entity.setProperty("nickname", nickname);
      entity.setProperty("address", address);
      entity.setProperty("phone", phone);
      entity.setProperty("country", country);
      entity.setProperty("zipcode", zipcode);
      entity.setProperty("location", location);
    }
    datastore.put(entity);

    // If task details were forwarded, then forward this request back to /tasks
    if (request.getParameterMap().containsKey("task-overview-input")) {
      RequestDispatcher rd = request.getRequestDispatcher("/tasks");
      rd.forward(request, response);
      return;
    }
    response.sendRedirect("/user_profile.jsp");
  }

  private List<User> retrieveTopTenUsers(
      HttpServletRequest request, UserService userService, DatastoreService datastore) {

    Query query = new Query("UserInfo").addSort("points", SortDirection.DESCENDING);

    // Adds additional filters for the nearby neighbors board
    if (request.getParameterMap().containsKey("lat-input")
        && request.getParameterMap().containsKey("lng-input")) {
      Float lat = null;
      Float lng = null;
      try {
        lat = Float.parseFloat(request.getParameter("lat-input"));
        lng = Float.parseFloat(request.getParameter("lng-input"));
      } catch (NumberFormatException e) {
        System.err.println("Invalid location coordinates");
        throw new IllegalArgumentException("Could not convert lat and/or lng to float");
      }
      GeoPt userLocation = new GeoPt(lat, lng);
      double FIVE_MILE_RADIUS = UnitConversion.milesToMeters(5);
      query.setFilter(
          new Query.StContainsFilter(
              "location", new Query.GeoRegion.Circle(userLocation, FIVE_MILE_RADIUS)));
    }

    // Gathers the top 10 results
    List<Entity> results = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(10));

    List<User> users = new ArrayList<>();

    for (Entity entity : results) {
      User user = new User(entity);
      if (userService.isUserLoggedIn()
          && user.getUserId().equals(userService.getCurrentUser().getUserId())) {
        user.setCurrentUser();
      }
      users.add(user);
    }
    return users;
  }
}
