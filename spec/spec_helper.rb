require 'capybara/rspec'
require 'active_support/all'
require 'selenium-webdriver'
#require 'chromedriver'
require 'capybara/poltergeist'
require 'pry'
require 'logger'

require 'helpers/misc'

# Chromedriver.set_version "2.29"

APP_ROOT_DIR = \
  Pathname(File.dirname(File.absolute_path(__FILE__))).parent

APP_BINARY =
  APP_ROOT_DIR.join(
    'madek-app-darwin-x64/madek-app.app/Contents/MacOS/madek-app').to_s

# binding.pry

RSpec.configure do |config|
  config.include Helpers::Misc

  Capybara.register_driver :chrome do |app|
    diver = Capybara::Selenium::Driver.new(
      app,
      :browser => :chrome,
      :desired_capabilities => Selenium::WebDriver::Remote::Capabilities.chrome(
        'chromeOptions' => {
          #'args' => [ "--window-size=200,200" ]
          'binary' => APP_BINARY
        }))
    # binding.pry
  end

  config.before(:all) do |example|
    Capybara.default_driver = :chrome
  end

  config.before(:each) do |example|
    Capybara.default_driver = :chrome

    # hack to get to the start page;
    # capybara requires us to `visit` something; otherwise there will be no window
    visit 'about:blank'
    execute_script 'window.history.back()'

  end


end


