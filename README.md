# digital-platform-reporting

Provides an interface between the frontend and the actual APIs.

## Nomenclature

- **Subscriber**:
    * A user who has registered and subscribed to the DPRS service

- **Subscription**:
    * The relationship between a subscriber and the DPRS service

## Technical documentation

This is a Scala app.

You can use the [GOV.UK Docker environment](https://github.com/alphagov/govuk-docker) to run the application and its tests with all the necessary dependencies. Follow [the usage instructions](https://github.com/alphagov/govuk-docker#usage) to get started.

**Use GOV.UK Docker to run any commands that follow.**

### Running the test suite

```bash
sbt test it/test
```

### Further documentation

Check the [docs/](docs/) directory for detailed instructions, decisions and other documentation.

## Licence

[MIT License](LICENCE)
