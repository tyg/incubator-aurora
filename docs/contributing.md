Getting your ReviewBoard Account
--------------------------------
Go to https://reviews.apache.org and create an account.

Setting up your ReviewBoard Environment
---------------------------------------
Run `./rbt status`. The first time this runs it will bootstrap and you will be asked to login.
Subsequent runs will cache your login credentials.

Submitting a Patch for Review (Committers)
------------------------------------------
First, push your feature branch, e.g.

    git push apache kevints/clairvoyance

Now post the review with `rbt`, fill out the fields in your browser and hit Publish.

    ./rbt post -o -g

Updating an Existing Review (Committers)
----------------------------------------
First, push your updated feature branch, e.g.

    git push apache kevints/clairvoyance

Now update the existing review, fill out the fields in your browser and hit Publish.

    ./rbt post -o -r <RB_ID>
