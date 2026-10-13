package com.publicrecords.collection.cryptid;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CryptidCinemaContent {
  private final List<Book> books = new ArrayList<>();
  private final List<Prompt> prompts = new ArrayList<>();
  public CryptidCinemaContent() {}
  public List<Book> books(boolean familyFriendly) { return Collections.unmodifiableList(books); }
  public List<Prompt> prompts(boolean familyFriendly) { return Collections.unmodifiableList(prompts); }
}
