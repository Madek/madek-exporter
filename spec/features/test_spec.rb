require 'spec_helper'

HERE = File.expand_path('.', __FILE__)

feature "The Madek App Window" do

  example "Is running and connects to the main process" do

    wait_until do
      not (page.has_content? "Loading ...")
    end

    wait_until do
      page.has_content? "Madek"
    end

    click_on 'About'
    # wait_until do
    #   page.has_content? /Electron-main DB .* nodejs-version/
    # end

    # check that we receive messages from electron-main and jvm-main
    click_on 'Debug'

    wait_until do
      page.has_content? "Electron main connected!"
    end

    wait_until do
      page.has_content? "JVM main connected!"
    end

  end
end

