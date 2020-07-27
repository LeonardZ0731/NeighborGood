// Copyright 2019 Google LLC
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
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import com.google.neighborgood.Message;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that loads and records new message entities. */
@WebServlet("/messages")
public class MessageServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String taskId = request.getParameter("key");
    if (taskId == null) {
      System.err.println("No task id provided");
    }

    // Make sure that the user has already logged into his account
    UserService userService = UserServiceFactory.getUserService();
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(userService.createLoginURL("/account.jsp"));
      return;
    }

    Filter filter = new FilterPredicate("taskId", FilterOperator.EQUAL, taskId);
    Query query =
        new Query("Message").setFilter(filter).addSort("sentTime", SortDirection.ASCENDING);

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    List<Message> messages = new ArrayList<>();
    for (Entity entity : results.asIterable()) {
      messages.add(new Message(entity));
    }

    Gson gson = new Gson();
    String json = gson.toJson(messages);
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // First check whether the user is logged in
    UserService userService = UserServiceFactory.getUserService();

    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(userService.createLoginURL("/account.jsp"));
      return;
    }

    // Get the task ID
    String taskId = request.getParameter("task-id");
    if (taskId == null) {
      System.err.println("The task id is not included");
      return;
    }

    // Get the message content
    String message = request.getParameter("msg");
    if (message == null || message.trim().equals("")) {
      System.err.println("The input message is empty");
      return;
    }

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    // Create a message entity to store the related information
    Entity msgEntity = new Entity("Message");
    msgEntity.setProperty("message", message);
    msgEntity.setProperty("taskId", taskId);
    msgEntity.setProperty("sender", userService.getCurrentUser().getUserId());
    msgEntity.setProperty("sentTime", System.currentTimeMillis());

    datastore.put(msgEntity);

    // After storing the message, create a notification entity to notify the receiver
    Key taskKey = KeyFactory.stringToKey(taskId);

    Entity taskEntity;
    try {
      taskEntity = datastore.get(taskKey);
    } catch (EntityNotFoundException e) {
      System.err.println("Unable to find the entity based on the input key");
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "The requested task could not be found");
      return;
    }

    String owner = (String) taskEntity.getProperty("Owner");
    String helper = (String) taskEntity.getProperty("Helper");
    String currentUser = userService.getCurrentUser().getUserId();

    Entity notificationEntity = new Entity("Notification");
    notificationEntity.setProperty("taskId", taskId);
    if (currentUser.equals(helper)) {
      notificationEntity.setProperty("receiver", owner);
    } else if (currentUser.equals(owner)) {
      notificationEntity.setProperty("receiver", helper);
    } else {
      System.err.println("The message is not sent by the owner or helper of the task");
      return;
    }
    datastore.put(notificationEntity);

    response.sendRedirect(request.getHeader("Referer"));
  }

  @Override
  public void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // First check whether the user is logged in
    UserService userService = UserServiceFactory.getUserService();

    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(userService.createLoginURL("/account.jsp"));
      return;
    }

    // Get the task ID
    String taskId = request.getParameter("task-id");
    if (taskId == null) {
      System.err.println("The task id is not included");
      return;
    }

    // Get all stored entities related with the task that corresponds to the given taskId
    Filter filter = new FilterPredicate("taskId", FilterOperator.EQUAL, taskId);
    Query query = new Query("Message").setFilter(filter);

    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery results = datastore.prepare(query);

    // Loop through all entities and delete them using the key
    for (Entity entity : results.asIterable()) {
      Key key = entity.getKey();
      datastore.delete(key);
    }

    response.sendRedirect(request.getHeader("Referer"));
  }
}
