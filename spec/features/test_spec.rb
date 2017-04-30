require 'spec_helper'

HERE = File.expand_path('.', __FILE__)

feature "Foo" do
  example "Bar" do
    expect(page).to have_content "Madek APP"
    wait_until do
      page.has_content? "Application loaded!"
    end
    wait_until 30 do
      page.has_content? 'JVM main state: Connected!'
    end
  end
end

